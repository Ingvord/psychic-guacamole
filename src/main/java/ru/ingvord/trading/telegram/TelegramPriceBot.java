package ru.ingvord.trading.telegram;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.ingvord.trading.service.Coingecko;
import ru.ingvord.trading.service.PriceService;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Telegram Price Tracking Bot - Lambda Function for Price Checking
 * 
 * This function runs on a schedule (via EventBridge) and:
 * 1. Fetches all active price alerts from DynamoDB
 * 2. Checks current cryptocurrency prices
 * 3. Sends Telegram notifications when price targets are reached
 */
public class TelegramPriceBot implements RequestHandler<ScheduledEvent, String> {

    public static final String DYNAMODB_ENDPOINT = System.getenv("DYNAMODB_ENDPOINT");
    private static final String TELEGRAM_BOT_TOKEN = System.getenv("TELEGRAM_BOT_TOKEN");
    public static final String TELEGRAM_BOT_URL = "https://api.telegram.org/bot" + TELEGRAM_BOT_TOKEN + "/sendMessage";
    private static final String ALERTS_TABLE = System.getenv("ALERTS_TABLE");

    private final HttpClient httpClient;
    private final PriceService priceService;
    private final DynamoDbClient dynamoDb;
    private final ObjectMapper objectMapper;
    
    public TelegramPriceBot() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        
        // Support local development with environment variable
        var builder = DynamoDbClient.builder()
            .region(Region.US_EAST_1);

        builder.endpointOverride(URI.create(DYNAMODB_ENDPOINT));
        System.out.println("Using local DynamoDB at: " + DYNAMODB_ENDPOINT);

        this.dynamoDb = builder.build();
        this.objectMapper = new ObjectMapper();
        this.priceService = new Coingecko(httpClient);
    }
    
    @Override
    public String handleRequest(ScheduledEvent input, Context context) {
        try {
            context.getLogger().log("Starting price check cycle...");
            
            // Get all active alerts from DynamoDB
            List<PriceAlert> alerts = getActiveAlerts();
            context.getLogger().log("Found " + alerts.size() + " active alerts");
            
            // Group alerts by symbol to minimize API calls
            Map<String, List<PriceAlert>> alertsBySymbol = groupAlertsBySymbol(alerts);
            
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
        try {
            ScanRequest scanRequest = ScanRequest.builder()
                .tableName(ALERTS_TABLE)
                .filterExpression("attribute_exists(chatId) AND attribute_exists(symbol)")
                .build();
            
            ScanResponse response = dynamoDb.scan(scanRequest);
            List<PriceAlert> alerts = new ArrayList<>();
            
            for (Map<String, AttributeValue> item : response.items()) {
                PriceAlert alert = new PriceAlert();
                alert.chatId = Long.parseLong(item.get("chatId").s());
                alert.symbol = item.get("symbol").s().toLowerCase();
                alert.targetPriceMin = item.containsKey("targetPriceMin") ? 
                    Double.parseDouble(item.get("targetPriceMin").n()) : 0.0;
                alert.targetPriceMax = item.containsKey("targetPriceMax") ? 
                    Double.parseDouble(item.get("targetPriceMax").n()) : Double.MAX_VALUE;
                alert.alertId = item.get("alertId").s();
                alerts.add(alert);
            }
            
            return alerts;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get alerts from DynamoDB", e);
        }
    }
    
    private Map<String, List<PriceAlert>> groupAlertsBySymbol(List<PriceAlert> alerts) {
        Map<String, List<PriceAlert>> grouped = new HashMap<>();
        for (PriceAlert alert : alerts) {
            grouped.computeIfAbsent(alert.symbol, k -> new ArrayList<>()).add(alert);
        }
        return grouped;
    }
    
    private BigDecimal getCurrentPrice(String symbol) throws IOException, InterruptedException {
        return this.priceService.getCurrentPrice(symbol);
    }
    
    private void checkAlertsAndNotify(List<PriceAlert> alerts, BigDecimal currentPrice, Context context) {
        for (PriceAlert alert : alerts) {
            boolean shouldNotify = false;
            String message = "";
            
            if (currentPrice.compareTo(BigDecimal.valueOf(alert.targetPriceMin)) <= 0 ){
                shouldNotify = true;
                message = String.format("🚨 Price Alert!\n\n%s has dropped to $%.2f\n(Below your target of $%.2f)",
                    alert.symbol.toUpperCase(), currentPrice, alert.targetPriceMin);
            } else if (currentPrice.compareTo(BigDecimal.valueOf(alert.targetPriceMax)) >= 0) {
                shouldNotify = true;
                message = String.format("🚀 Price Alert!\n\n%s has risen to $%.2f\n(Above your target of $%.2f)",
                    alert.symbol.toUpperCase(), currentPrice, alert.targetPriceMax);
            }
            
            if (shouldNotify) {
                try {
                    sendTelegramMessage(alert.chatId, message);
                    context.getLogger().log("Alert sent for " + alert.symbol + " to chat " + alert.chatId);
                    
                    // Optionally disable the alert after triggering
                    // disableAlert(alert.alertId);
                    
                } catch (Exception e) {
                    context.getLogger().log("Failed to send alert: " + e.getMessage());
                }
            }
        }
    }
    
    private void sendTelegramMessage(long chatId, String message) throws IOException, InterruptedException {
        Map<String, Object> payload = Map.of(
            "chat_id", chatId,
            "text", message,
            "parse_mode", "Markdown"
        );
        
        String jsonPayload = objectMapper.writeValueAsString(payload);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(TELEGRAM_BOT_URL))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(10))
            .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new IOException("Failed to send Telegram message: " + response.body());
        }
    }
    
    private void disableAlert(String alertId) {
        try {
            UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName(ALERTS_TABLE)
                .key(Map.of("alertId", AttributeValue.builder().s(alertId).build()))
                .updateExpression("SET alertStatus = :status")
                .expressionAttributeValues(Map.of(
                    ":status", AttributeValue.builder().s("TRIGGERED").build()
                ))
                .build();
            
            dynamoDb.updateItem(updateRequest);
        } catch (Exception e) {
            System.err.println("Failed to disable alert: " + e.getMessage());
        }
    }
    
    /**
     * Data class for price alerts
     */
    private static class PriceAlert {
        long chatId;
        String symbol;
        double targetPriceMin;
        double targetPriceMax;
        String alertId;
    }
}