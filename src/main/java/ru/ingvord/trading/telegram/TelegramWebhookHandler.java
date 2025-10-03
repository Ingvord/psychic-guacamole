package ru.ingvord.trading.telegram;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.ingvord.trading.repository.AlertRepository;
import ru.ingvord.trading.service.Coingecko;
import ru.ingvord.trading.service.PriceService;
import ru.ingvord.trading.service.TelegramBotCommandService;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;

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
public class TelegramWebhookHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>, AutoCloseable {
    public static final String BOT_NAME = "AdeptConstructionTechnicianBot";

    private final HttpClient httpClient;
    private final DynamoDbClient dynamoDb;
    private final TelegramBotCommandService telegramCommandService;

    {
        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
    }

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

        String alertsTable = Optional.ofNullable(System.getenv("DYNAMODB_ENDPOINT")).orElse("price-alerts-local");

        this.dynamoDb = builder.build();
        this.telegramCommandService = new TelegramBotCommandService(
                new Coingecko(httpClient),
                new AlertRepository(alertsTable, dynamoDb));

    }
    
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            context.getLogger().log("Received webhook.");
            var verified = verifyTelegramRequest(input, context);
            if(!verified){
                context.getLogger().log("Invalid request: secret token does not match");
                return createResponse(200, "OK");
            }

            TelegramUpdate update = TelegramUpdate.parse(input.getBody());
            context.getLogger().log("Handling command: " + update.text);
            var response = telegramCommandService.execute(update, context);
            // Send response to Telegram
            if (response != null && !response.isEmpty()) {
                sendTelegramMessage(update.chatId, response);
            }
            return createResponse(200, "OK");
        } catch (JsonProcessingException e) {
            context.getLogger().log("Invalid JSON: " + e.getMessage());
            return createResponse(200, "OK");
        } catch (DynamoDbException e) {
            context.getLogger().log("Database error: " + e.getMessage());
            return createResponse(200, "OK");
        } catch (Exception e) {
            context.getLogger().log("Unexpected error: " + e.getClass().getName());
            return createResponse(200, "OK");
        }
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

    @Override
    public void close() {
        dynamoDb.close();
        httpClient.close();
    }

    private boolean verifyTelegramRequest(APIGatewayProxyRequestEvent input, Context context) {
        String expectedToken = System.getenv("WEBHOOK_SECRET_TOKEN");
        if (expectedToken == null || expectedToken.isEmpty()) {
            context.getLogger().log("WARNING: No webhook secret configured");
            return true; // Allow in dev
        }

        String receivedToken = getHeaderCaseInsensitive(input, "X-Telegram-Bot-Api-Secret-Token");
        if (receivedToken == null || !expectedToken.equals(receivedToken)) {
            context.getLogger().log("SECURITY: Invalid webhook signature");
            return false;
        }
        return true;
    }

    private void sendTelegramMessage(long chatId, String message) throws IOException, InterruptedException {
        var telegramBotToken = System.getenv("TELEGRAM_BOT_TOKEN");
        if (telegramBotToken == null || telegramBotToken.isEmpty()) {
            throw new IllegalStateException("TELEGRAM_BOT_TOKEN is required");
        }
        new SendMessage(telegramBotToken, String.valueOf(chatId), message).execute();
    }

    private String getHeaderCaseInsensitive(APIGatewayProxyRequestEvent input, String header) {
        return Optional.ofNullable(input.getHeaders().get(header))
                .orElse(input.getHeaders().get(header.toLowerCase()));
    }
}