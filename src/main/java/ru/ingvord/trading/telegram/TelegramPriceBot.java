package ru.ingvord.trading.telegram;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import ru.ingvord.trading.repository.AlertRepository;
import ru.ingvord.trading.repository.PriceAlert;
import ru.ingvord.trading.repository.SecretRepository;
import ru.ingvord.trading.service.Coingecko;
import ru.ingvord.trading.service.PriceService;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
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
    private final AlertRepository alertRepository;

    private final String botApiToken;
    private final HttpClient httpClient;
    private final DynamoDbClient dynamoDb;

    public TelegramPriceBot() throws IOException {
        this.botApiToken = new SecretRepository().getParameter(TELEGRAM_BOT_TOKEN);

        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        // Support local development with environment variable
        var builder = DynamoDbClient.builder()
                .region(Region.EU_CENTRAL_1);

        String endpoint = DYNAMODB_ENDPOINT;
        if (endpoint != null && !endpoint.isEmpty()) {
            builder.endpointOverride(URI.create(endpoint));
            System.out.println("Using local DynamoDB at: " + endpoint);
        }

        this.dynamoDb = builder.build();
        this.priceService = new Coingecko(httpClient);
        this.alertRepository = new AlertRepository(ALERTS_TABLE, dynamoDb);
    }
    
    @Override
    public String handleRequest(ScheduledEvent input, Context context) {
        try {
            context.getLogger().log("Starting price check cycle...");
            
            // Get all active alerts from DynamoDB
            Map<String, List<PriceAlert>> alertsBySymbol = getActiveAlerts().stream()
                    .filter(priceAlert -> ALERT_STATUS_ACTIVE.equalsIgnoreCase(priceAlert.status()))
                    .collect(
                        Collectors.groupingBy(PriceAlert::symbol));

            List<CompletableFuture<Void>> futures = alertsBySymbol.entrySet().stream()
                    .map(entry -> CompletableFuture.supplyAsync(() -> {
                        try {
                            return getCurrentPrice(entry.getKey());
                        } catch (Exception e) {
                            context.getLogger().log("Error: " + e.getMessage());
                            return null;
                        }
                    }).thenAccept(price -> {
                        if (price != null) {
                            checkAlertsAndNotify(entry.getValue(), price, context);
                        }
                    }))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            return "Price check completed successfully";
            
        } catch (Exception e) {
            context.getLogger().log("Error in price check: " + e.getMessage());
            return "Price check failed: " + e.getMessage();
        }
    }
    
    private List<PriceAlert> getActiveAlerts() {
        return alertRepository.findByStatus(ALERT_STATUS_ACTIVE);
    }

    private BigDecimal getCurrentPrice(String symbol) throws IOException, InterruptedException {
        return this.priceService.getCurrentPrice(symbol);
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
        new SendMessage(botApiToken, String.valueOf(chatId), message).execute();
    }
    
    private void disableAlert(String alertId) {
        alertRepository.updateStatus(alertId, ALERT_STATUS_TRIGGERED);
    }

    @Override
    public void close() {
        dynamoDb.close();
        httpClient.close();
    }
}