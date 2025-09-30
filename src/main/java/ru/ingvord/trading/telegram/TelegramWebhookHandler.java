package ru.ingvord.trading.telegram;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Telegram Webhook Handler for Bot Commands
 * 
 * Handles incoming Telegram updates via API Gateway webhook and processes user commands.
 * 
 * Supported Commands:
 * /start - Welcome message and instructions
 * /help - Show available commands
 * /alert <symbol> <min_price> <max_price> - Set price alert
 * /list - List all active alerts
 * /remove <alert_id> - Remove specific alert
 * /clear - Remove all alerts for user
 * /price <symbol> - Get current price
 */
public class TelegramWebhookHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    
    private static final String TELEGRAM_BOT_TOKEN = System.getenv("TELEGRAM_BOT_TOKEN");
    private static final String ALERTS_TABLE = System.getenv("ALERTS_TABLE");
    private static final String COINGECKO_API = "https://api.coingecko.com/api/v3/simple/price";
    
    private final HttpClient httpClient;
    private final DynamoDbClient dynamoDb;
    private final ObjectMapper objectMapper;
    
    // Command patterns
    private static final Pattern ALERT_PATTERN = Pattern.compile("/alert\\s+(\\w+)\\s+([\\d.]+)\\s+([\\d.]+)");
    private static final Pattern PRICE_PATTERN = Pattern.compile("/price\\s+(\\w+)");
    private static final Pattern REMOVE_PATTERN = Pattern.compile("/remove\\s+(\\w+)");
    
    public TelegramWebhookHandler() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        
        // Support local development with environment variable
        var builder = DynamoDbClient.builder()
            .region(Region.US_EAST_1);
        
        String endpoint = System.getenv("DYNAMODB_ENDPOINT");
        if (endpoint != null && !endpoint.isEmpty()) {
            builder.endpointOverride(URI.create(endpoint));
            System.out.println("Using local DynamoDB at: " + endpoint);
        }
        
        this.dynamoDb = builder.build();
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            context.getLogger().log("Received webhook: " + input.getBody());
            
            // Parse Telegram update
            JsonNode update = objectMapper.readTree(input.getBody());
            
            if (!update.has("message")) {
                return createResponse(200, "No message in update");
            }
            
            JsonNode message = update.get("message");
            long chatId = message.get("chat").get("id").asLong();
            String text = message.has("text") ? message.get("text").asText() : "";
            
            // Handle the command
            String response = handleCommand(chatId, text, context);
            
            // Send response to Telegram
            if (response != null && !response.isEmpty()) {
                sendTelegramMessage(chatId, response);
            }
            
            return createResponse(200, "OK");
            
        } catch (Exception e) {
            context.getLogger().log("Error handling webhook: " + e.getMessage());
            return createResponse(500, "Error: " + e.getMessage());
        }
    }
    
    private String handleCommand(long chatId, String text, Context context) {
        context.getLogger().log("Handling command: " + text + " from chat: " + chatId);
        
        if (text.startsWith("/start")) {
            return getWelcomeMessage();
        } else if (text.startsWith("/help")) {
            return getHelpMessage();
        } else if (text.startsWith("/alert")) {
            return handleAlertCommand(chatId, text, context);
        } else if (text.startsWith("/list")) {
            return handleListCommand(chatId, context);
        } else if (text.startsWith("/remove")) {
            return handleRemoveCommand(chatId, text, context);
        } else if (text.startsWith("/clear")) {
            return handleClearCommand(chatId, context);
        } else if (text.startsWith("/price")) {
            return handlePriceCommand(text, context);
        } else if (text.startsWith("/")) {
            return "Unknown command. Type /help to see available commands.";
        }
        
        return null; // Don't respond to non-commands
    }
    
    private String getWelcomeMessage() {
        return """
            🤖 **Welcome to Price Alert Bot!**
            
            I can help you track cryptocurrency prices and send you alerts when they reach your target ranges.
            
            **Quick Start:**
            • Use `/alert bitcoin 30000 50000` to set alerts
            • Use `/price bitcoin` to check current prices
            • Use `/list` to see your active alerts
            
            Type `/help` for all available commands.
            """;
    }
    
    private String getHelpMessage() {
        return """
            📋 **Available Commands:**
            
            **Price Tracking:**
            • `/alert <symbol> <min> <max>` - Set price alert
              Example: `/alert bitcoin 30000 50000`
            
            • `/price <symbol>` - Get current price
              Example: `/price ethereum`
            
            **Alert Management:**
            • `/list` - Show all your active alerts
            • `/remove <alert_id>` - Remove specific alert
            • `/clear` - Remove all your alerts
            
            **Other:**
            • `/help` - Show this message
            • `/start` - Welcome message
            
            **Supported Symbols:** bitcoin, ethereum, cardano, solana, dogecoin, and many more!
            """;
    }
    
    private String handleAlertCommand(long chatId, String text, Context context) {
        Matcher matcher = ALERT_PATTERN.matcher(text);
        if (!matcher.matches()) {
            return """
                ❌ **Invalid alert format!**
                
                **Correct usage:**
                `/alert <symbol> <min_price> <max_price>`
                
                **Example:**
                `/alert bitcoin 30000 50000`
                
                This will alert you when Bitcoin goes below $30,000 or above $50,000.
                """;
        }
        
        String symbol = matcher.group(1).toLowerCase();
        double minPrice = Double.parseDouble(matcher.group(2));
        double maxPrice = Double.parseDouble(matcher.group(3));
        
        if (minPrice >= maxPrice) {
            return "❌ Minimum price must be less than maximum price!";
        }
        
        try {
            // Verify symbol exists by checking current price
            double currentPrice = getCurrentPrice(symbol);
            
            // Create alert
            String alertId = UUID.randomUUID().toString();
            createAlert(alertId, chatId, symbol, minPrice, maxPrice);
            
            return String.format("""
                ✅ **Alert Created!**
                
                **Symbol:** %s
                **Current Price:** $%.2f
                **Alert Range:** $%.2f - $%.2f
                **Alert ID:** `%s`
                
                You'll be notified when %s goes outside this range.
                """, 
                symbol.toUpperCase(), currentPrice, minPrice, maxPrice, alertId.substring(0, 8),
                symbol.toUpperCase());
                
        } catch (Exception e) {
            context.getLogger().log("Error creating alert: " + e.getMessage());
            return "❌ Failed to create alert. Please check the symbol name and try again.\n\n" +
                   "Popular symbols: bitcoin, ethereum, cardano, solana, dogecoin";
        }
    }
    
    private String handleListCommand(long chatId, Context context) {
        try {
            List<Map<String, AttributeValue>> alerts = getUserAlerts(chatId);
            
            if (alerts.isEmpty()) {
                return """
                    📭 **No Active Alerts**
                    
                    You don't have any active price alerts yet.
                    
                    Create one with: `/alert bitcoin 30000 50000`
                    """;
            }
            
            StringBuilder response = new StringBuilder("📊 **Your Active Alerts:**\n\n");
            
            for (Map<String, AttributeValue> alert : alerts) {
                String alertId = alert.get("alertId").s();
                String symbol = alert.get("symbol").s().toUpperCase();
                double minPrice = Double.parseDouble(alert.get("targetPriceMin").n());
                double maxPrice = Double.parseDouble(alert.get("targetPriceMax").n());
                
                response.append(String.format(
                    "🔔 **%s**\n" +
                    "   Range: $%.2f - $%.2f\n" +
                    "   ID: `%s`\n\n",
                    symbol, minPrice, maxPrice, alertId.substring(0, 8)
                ));
            }
            
            response.append("Use `/remove <alert_id>` to remove specific alerts\n");
            response.append("Use `/clear` to remove all alerts");
            
            return response.toString();
            
        } catch (Exception e) {
            context.getLogger().log("Error listing alerts: " + e.getMessage());
            return "❌ Failed to retrieve your alerts. Please try again later.";
        }
    }
    
    private String handleRemoveCommand(long chatId, String text, Context context) {
        Matcher matcher = REMOVE_PATTERN.matcher(text);
        if (!matcher.matches()) {
            return """
                ❌ **Invalid remove format!**
                
                **Correct usage:**
                `/remove <alert_id>`
                
                **Example:**
                `/remove a1b2c3d4`
                
                Use `/list` to see your alert IDs.
                """;
        }
        
        String alertIdPrefix = matcher.group(1);
        
        try {
            List<Map<String, AttributeValue>> alerts = getUserAlerts(chatId);
            String fullAlertId = null;
            
            // Find matching alert by prefix
            for (Map<String, AttributeValue> alert : alerts) {
                String alertId = alert.get("alertId").s();
                if (alertId.startsWith(alertIdPrefix)) {
                    fullAlertId = alertId;
                    break;
                }
            }
            
            if (fullAlertId == null) {
                return "❌ Alert not found. Use `/list` to see your active alerts.";
            }
            
            removeAlert(fullAlertId);
            
            return "✅ Alert removed successfully!";
            
        } catch (Exception e) {
            context.getLogger().log("Error removing alert: " + e.getMessage());
            return "❌ Failed to remove alert. Please try again later.";
        }
    }
    
    private String handleClearCommand(long chatId, Context context) {
        try {
            List<Map<String, AttributeValue>> alerts = getUserAlerts(chatId);
            
            if (alerts.isEmpty()) {
                return "📭 You don't have any active alerts to clear.";
            }
            
            for (Map<String, AttributeValue> alert : alerts) {
                removeAlert(alert.get("alertId").s());
            }
            
            return String.format("✅ Cleared %d alerts successfully!", alerts.size());
            
        } catch (Exception e) {
            context.getLogger().log("Error clearing alerts: " + e.getMessage());
            return "❌ Failed to clear alerts. Please try again later.";
        }
    }
    
    private String handlePriceCommand(String text, Context context) {
        Matcher matcher = PRICE_PATTERN.matcher(text);
        if (!matcher.matches()) {
            return """
                ❌ **Invalid price format!**
                
                **Correct usage:**
                `/price <symbol>`
                
                **Example:**
                `/price bitcoin`
                """;
        }
        
        String symbol = matcher.group(1).toLowerCase();
        
        try {
            double currentPrice = getCurrentPrice(symbol);
            
            return String.format("""
                💰 **Current Price**
                
                **%s:** $%.2f
                
                Want to set an alert? Use:
                `/alert %s <min_price> <max_price>`
                """, 
                symbol.toUpperCase(), currentPrice, symbol);
                
        } catch (Exception e) {
            context.getLogger().log("Error getting price: " + e.getMessage());
            return "❌ Failed to get price. Please check the symbol name and try again.\n\n" +
                   "Popular symbols: bitcoin, ethereum, cardano, solana, dogecoin";
        }
    }
    
    private double getCurrentPrice(String symbol) throws IOException, InterruptedException {
        String url = COINGECKO_API + "?ids=" + symbol + "&vs_currencies=usd";
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(10))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new IOException("API request failed with status: " + response.statusCode());
        }
        
        JsonNode jsonNode = objectMapper.readTree(response.body());
        JsonNode priceNode = jsonNode.get(symbol);
        
        if (priceNode == null || !priceNode.has("usd")) {
            throw new IOException("Price not found for symbol: " + symbol);
        }
        
        return priceNode.get("usd").asDouble();
    }
    
    private void createAlert(String alertId, long chatId, String symbol, double minPrice, double maxPrice) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("alertId", AttributeValue.builder().s(alertId).build());
        item.put("chatId", AttributeValue.builder().s(String.valueOf(chatId)).build());
        item.put("symbol", AttributeValue.builder().s(symbol).build());
        item.put("targetPriceMin", AttributeValue.builder().n(String.valueOf(minPrice)).build());
        item.put("targetPriceMax", AttributeValue.builder().n(String.valueOf(maxPrice)).build());
        item.put("createdAt", AttributeValue.builder().s(String.valueOf(System.currentTimeMillis())).build());
        item.put("status", AttributeValue.builder().s("ACTIVE").build());
        
        PutItemRequest putRequest = PutItemRequest.builder()
            .tableName(ALERTS_TABLE)
            .item(item)
            .build();
        
        dynamoDb.putItem(putRequest);
    }
    
    private List<Map<String, AttributeValue>> getUserAlerts(long chatId) {
        QueryRequest queryRequest = QueryRequest.builder()
            .tableName(ALERTS_TABLE)
            .indexName("ChatIdIndex")
            .keyConditionExpression("chatId = :chatId")
            .filterExpression("attribute_not_exists(#status) OR #status = :status")
            .expressionAttributeNames(Map.of("#status", "status"))
            .expressionAttributeValues(Map.of(
                ":chatId", AttributeValue.builder().s(String.valueOf(chatId)).build(),
                ":status", AttributeValue.builder().s("ACTIVE").build()
            ))
            .build();
        
        QueryResponse response = dynamoDb.query(queryRequest);
        return response.items();
    }
    
    private void removeAlert(String alertId) {
        DeleteItemRequest deleteRequest = DeleteItemRequest.builder()
            .tableName(ALERTS_TABLE)
            .key(Map.of("alertId", AttributeValue.builder().s(alertId).build()))
            .build();
        
        dynamoDb.deleteItem(deleteRequest);
    }
    
    private void sendTelegramMessage(long chatId, String message) throws IOException, InterruptedException {
        String url = "https://api.telegram.org/bot" + TELEGRAM_BOT_TOKEN + "/sendMessage";
        
        Map<String, Object> payload = Map.of(
            "chat_id", chatId,
            "text", message,
            "parse_mode", "Markdown"
        );
        
        String jsonPayload = objectMapper.writeValueAsString(payload);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(10))
            .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
            .build();
        
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
    
    private APIGatewayProxyResponseEvent createResponse(int statusCode, String body) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setStatusCode(statusCode);
        response.setBody(body);
        response.setHeaders(Map.of(
            "Content-Type", "application/json",
            "Access-Control-Allow-Origin", "*"
        ));
        return response;
    }
}