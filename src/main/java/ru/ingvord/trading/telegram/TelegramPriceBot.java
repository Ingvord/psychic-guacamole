package ru.ingvord.trading.telegram;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.amazonaws.services.lambda.runtime.logging.LogLevel;
import ru.ingvord.trading.repository.AlertRepository;
import ru.ingvord.trading.model.PriceAlert;
import ru.ingvord.trading.model.RsiAlert;
import ru.ingvord.trading.repository.SecretRepository;
import ru.ingvord.trading.service.*;
import ru.ingvord.trading.util.TracedHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static ru.ingvord.trading.environment.Variables.*;
import static ru.ingvord.trading.repository.AlertRepository.ALERT_STATUS_ACTIVE;
import static ru.ingvord.trading.repository.AlertRepository.ALERT_STATUS_TRIGGERED;

/**
 * Telegram Price Tracking Bot - Lambda Function for Price Checking
 * 
 * This function runs on a schedule (via EventBridge) and:
 * 1. Fetches all active price alerts from DynamoDB
 * 2. Checks current cryptocurrency prices
 * 3. Sends Telegram notifications when price targets are reached
 */
public class TelegramPriceBot implements RequestHandler<ScheduledEvent, String>, AutoCloseable{
    public static final String BOT_NAME = "AdeptConstructionTechnicianBot";

    private final PriceService priceService;
    private final IndicatorService indicatorService;
    private final AlertRepository alertRepository;

    private final String botApiToken;
    private final HttpClient httpClient;
    private final DynamoDbClient dynamoDb; // null when constructed with injected collaborators

    /** Seam for tests: inject everything instead of building AWS/HTTP clients. */
    TelegramPriceBot(PriceService priceService, IndicatorService indicatorService,
                     AlertRepository alertRepository, String botApiToken, HttpClient httpClient) {
        this.priceService = priceService;
        this.indicatorService = indicatorService;
        this.alertRepository = alertRepository;
        this.botApiToken = botApiToken;
        this.httpClient = httpClient;
        this.dynamoDb = null;
    }

    public TelegramPriceBot() throws IOException {
        this.botApiToken = new SecretRepository().getParameter(TELEGRAM_BOT_TOKEN);

        this.httpClient = new TracedHttpClient(HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build());

        // Support local development with environment variable
        var builder = DynamoDbClient.builder()
                .region(Region.EU_CENTRAL_1);

        String endpoint = DYNAMODB_ENDPOINT;
        if (endpoint != null && !endpoint.isEmpty()) {
            builder.endpointOverride(URI.create(endpoint));
            System.out.println("Using local DynamoDB at: " + endpoint);
        }

        this.dynamoDb = builder.build();
        var bybit = new Bybit(httpClient);
        this.priceService = bybit;
        this.indicatorService = bybit;
        this.alertRepository = new AlertRepository(ALERTS_TABLE, dynamoDb);
    }
    
    @Override
    public String handleRequest(ScheduledEvent input, Context context) {
        try {
            context.getLogger().log("Starting price check cycle...");
            
            // OPTIMIZATION 1: Get unique symbols for batch request
            List<PriceAlert> activeAlerts = getActiveAlerts();
            Set<String> uniqueSymbols = activeAlerts.stream()
                    .map(PriceAlert::symbol)
                    .map(String::toUpperCase)
                    .collect(Collectors.toSet());


            // Fetch all prices in ONE API call
            Map<String, BigDecimal> currentPrices = priceService.getPrices(uniqueSymbols);

            Map<Long, List<TriggeredAlert>> triggeredAlertsByUser = new HashMap<>();

            // Check each alert against current prices
            for (PriceAlert alert : activeAlerts) {
                BigDecimal currentPrice = currentPrices.get(alert.symbol().toUpperCase());

                if (currentPrice == null) {
                    context.getLogger().log("Warning: No price found for " + alert.symbol(), LogLevel.WARN);
                    continue;
                }

                var triggered = checkAlert(alert, currentPrice);
                if (triggered != null) {
                    triggeredAlertsByUser
                            .computeIfAbsent(alert.chatId(), k -> new ArrayList<>())
                            .add(triggered);
                }
            }

            int notificationsSent = 0;
            for (Map.Entry<Long, List<TriggeredAlert>> entry : triggeredAlertsByUser.entrySet()) {
                long chatId = entry.getKey();
                List<TriggeredAlert> userAlerts = entry.getValue();

                try {
                    String combinedMessage = buildCombinedNotification(userAlerts);
                    sendTelegramMessage(chatId, combinedMessage);
                    notificationsSent++;

                    context.getLogger().log(
                            "Sent notification to user " + chatId +
                                    " for " + userAlerts.size() + " triggered alerts"
                    );
                } catch (IOException | InterruptedException e) {
                    context.getLogger().log(
                            "Failed to send notification to user " + chatId + ": " + e.getMessage(),
                            LogLevel.ERROR
                    );
                }
            }

            checkRsiAlerts(context);

            String summary = String.format(
                    "Price check completed: %d alerts checked, %d symbols fetched, %d users notified",
                    activeAlerts.size(), uniqueSymbols.size(), notificationsSent
            );

            context.getLogger().log(summary,
                    notificationsSent == triggeredAlertsByUser.size() ? LogLevel.INFO : LogLevel.WARN);
            return summary;
            
        } catch (Exception e) {
            context.getLogger().log("Error in price check: " + e.getMessage());
            return "Price check failed: " + e.getMessage();
        }
    }

    /**
     * Notify users when RSI enters the overbought/oversold zone.
     * Fires on zone change only; wrapped in its own try/catch so it never breaks price alerts.
     */
    private void checkRsiAlerts(Context context) {
        try {
            record RsiKey(String symbol, String interval, int period, String seriesType) {}

            Map<RsiKey, List<RsiAlert>> groups = alertRepository.findRsiByStatus().stream()
                    .collect(Collectors.groupingBy(a ->
                            new RsiKey(a.symbol(), a.interval(), a.period(), a.seriesType())));

            Map<Long, StringBuilder> messages = new HashMap<>();

            // One API call per distinct (symbol, interval, period, series) combination
            for (var group : groups.entrySet()) {
                RsiKey key = group.getKey();
                BigDecimal rsi;
                try {
                    rsi = indicatorService.getRsi(key.symbol(), key.interval(), key.period(), key.seriesType());
                } catch (IOException | InterruptedException e) {
                    context.getLogger().log("RSI fetch failed for " + key + ": " + e.getMessage(), LogLevel.WARN);
                    continue;
                }

                String zone = RsiAlert.zoneOf(rsi);
                for (RsiAlert alert : group.getValue()) {
                    if (zone.equals(alert.lastZone())) {
                        continue;
                    }
                    alertRepository.updateRsiZone(alert.alertId(), zone);

                    if (!RsiAlert.ZONE_NORMAL.equals(zone)) {
                        boolean overbought = RsiAlert.ZONE_OVERBOUGHT.equals(zone);
                        messages.computeIfAbsent(alert.chatId(), k -> new StringBuilder("📈 RSI Alert!\n\n"))
                                .append(String.format("%s %s RSI(%d) is %.2f — %s\n\n",
                                        overbought ? "🔥" : "🧊",
                                        alert.symbol().toUpperCase() + " " + alert.interval(),
                                        alert.period(), rsi,
                                        overbought ? "overbought" : "oversold"));
                    }
                }
            }

            for (var entry : messages.entrySet()) {
                try {
                    sendTelegramMessage(entry.getKey(), entry.getValue().toString());
                } catch (IOException | InterruptedException e) {
                    context.getLogger().log("Failed to send RSI notification to " + entry.getKey() + ": " + e.getMessage(), LogLevel.ERROR);
                }
            }
        } catch (Exception e) {
            context.getLogger().log("Error in RSI check: " + e.getMessage(), LogLevel.ERROR);
        }
    }

    /**
     * Build combined notification message for multiple triggered alerts
     */
    private String buildCombinedNotification(List<TriggeredAlert> alerts) {
        String combinedMessage = "🔔 Price Alert!\n\n";
        return combinedMessage + alerts.stream()
                .map(TriggeredAlert::toString)
                .collect(Collectors.joining(""));
    }
    
    private List<PriceAlert> getActiveAlerts() {
        return alertRepository.findByStatus(ALERT_STATUS_ACTIVE);
    }

    private BigDecimal getCurrentPrice(String symbol) throws IOException, InterruptedException {
        return this.priceService.getCurrentPrice(symbol);
    }

    private TriggeredAlert checkAlert(PriceAlert alert, BigDecimal currentPrice){
        if (currentPrice.compareTo(alert.targetPriceMin()) <= 0 ){
            return new TriggeredAlert(alert, currentPrice, TriggerType.BELOW_MIN);
        } else if (currentPrice.compareTo(alert.targetPriceMax()) >= 0) {
            return new TriggeredAlert(alert, currentPrice, TriggerType.ABOVE_MAX);
        } else
            return null;
    }
    
    private void checkAlertsAndNotify(List<PriceAlert> alerts, BigDecimal currentPrice, Context context) {
        for (PriceAlert alert : alerts) {
            boolean shouldNotify = false;
            String message = "";
            
            if (currentPrice.compareTo(alert.targetPriceMin()) <= 0 ){
                shouldNotify = true;
                message = String.format("🚨 Price Alert!\n\n%s has dropped to $%.2f\n(Below your target of $%.2f)",
                    alert.symbol().toUpperCase(), currentPrice, alert.targetPriceMin());
            } else if (currentPrice.compareTo(alert.targetPriceMax()) >= 0) {
                shouldNotify = true;
                message = String.format("🚀 Price Alert!\n\n%s has risen to $%.2f\n(Above your target of $%.2f)",
                    alert.symbol().toUpperCase(), currentPrice, alert.targetPriceMax());
            }
            
            if (shouldNotify) {
                try {
                    sendTelegramMessage(alert.chatId(), message);

                    // Disable the alert after triggering
                    disableAlert(alert.alertId());
                } catch (Exception e) {
                    context.getLogger().log("Failed to send alert: " + e.getMessage());
                }
            }
        }
    }
    
    private void sendTelegramMessage(long chatId, String message) throws IOException, InterruptedException {
        new SendMessage(botApiToken, String.valueOf(chatId), message, httpClient).execute();
    }
    
    private void disableAlert(String alertId) {
        alertRepository.updateStatus(alertId, ALERT_STATUS_TRIGGERED);
    }

    @Override
    public void close() {
        if (dynamoDb != null) dynamoDb.close();
        httpClient.close();
    }

    /**
     * Represents a triggered alert with current price and trigger type
     */
    private record TriggeredAlert(
            PriceAlert alert,
            BigDecimal currentPrice,
            TriggerType type
    ) {
        @Override
        public String toString() {
            String name = alert.symbol().toUpperCase();

            if(type == TriggerType.BELOW_MIN){
                return String.format("🚨 %s has dropped to $%,.2f\n(Below your target of $%,.2f)\n\n",
                        name, currentPrice, alert.targetPriceMin());
            } else if(type == TriggerType.ABOVE_MAX){
                return String.format("🚀 %s has risen to $%,.2f\n(Above your target of $%,.2f)\n\n",
                        name, currentPrice, alert.targetPriceMax());
            } else {
                return "Something went wrong!";// :-P
            }
        }

    }

    private enum TriggerType {
        BELOW_MIN,
        ABOVE_MAX
    }
}