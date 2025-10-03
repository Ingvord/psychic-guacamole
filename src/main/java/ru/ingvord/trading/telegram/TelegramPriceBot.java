package ru.ingvord.trading.telegram;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import ru.ingvord.trading.repository.AlertRepository;
import ru.ingvord.trading.repository.PriceAlert;
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
import java.util.stream.Collectors;

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

    private final String alertsTable;
    private final String botApiToken;
    private final String dynamodbEndpoint;
    private final HttpClient httpClient;
    private final DynamoDbClient dynamoDb;

    {
        alertsTable = System.getenv("ALERTS_TABLE");
        botApiToken = System.getenv("TELEGRAM_BOT_TOKEN");
        dynamodbEndpoint =  System.getenv("DYNAMODB_ENDPOINT");
    }

    public TelegramPriceBot() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        
        this.dynamoDb = DynamoDbClient.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create(dynamodbEndpoint))
                .build();
        this.priceService = new Coingecko(httpClient);
        this.alertRepository = new AlertRepository(alertsTable, dynamoDb);
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

            // Check prices and send notifications
            for (Map.Entry<String, List<PriceAlert>> entry : alertsBySymbol.entrySet()) {
                String symbol = entry.getKey();
                List<PriceAlert> symbolAlerts = entry.getValue();
                
                try {
                    BigDecimal currentPrice = getCurrentPrice(symbol);
                    context.getLogger().log(symbol + " current price: $" + currentPrice);
                    
                    checkAlertsAndNotify(symbolAlerts, currentPrice, context);
                } catch (Exception e) {
                    context.getLogger().log("Error checking price for " + symbol + ": " + e.getMessage());
                }
            }
            
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