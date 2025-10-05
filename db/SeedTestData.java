///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS software.amazon.awssdk:dynamodb:2.21.29
//DEPS software.amazon.awssdk:regions:2.21.29

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.regions.Region;

import java.math.BigDecimal;
import java.net.URI;
import java.util.*;

public class SeedTestData {

    private static final String TABLE_NAME = "price-alerts-local";
    private static final String ENDPOINT = "http://localhost:8000";

    public static void main(String[] args) {
        System.out.println("🌱 Seeding Test Data for DynamoDB");
        System.out.println("==================================");

        // Connect to local DynamoDB
        DynamoDbClient dynamoDb = DynamoDbClient.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create(ENDPOINT))
                .build();

        try {
            // Verify table exists
            verifyTable(dynamoDb);

            // Clear existing data (optional)
            if (shouldClearExistingData()) {
                clearExistingData(dynamoDb);
            }

            // Create test alerts
            List<TestAlert> testAlerts = createTestAlerts();

            // Insert alerts
            int count = 0;
            for (TestAlert alert : testAlerts) {
                insertAlert(dynamoDb, alert);
                count++;
                System.out.println("✅ Created alert " + count + ": " + alert.description);
            }

            System.out.println();
            System.out.println("🎉 Successfully seeded " + count + " test alerts!");
            System.out.println();

            // Display summary
            displaySummary(dynamoDb);

            // Show how to test
            showTestingInstructions();

        } catch (Exception e) {
            System.err.println("❌ Error seeding data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Verify that the table exists
     */
    private static void verifyTable(DynamoDbClient dynamoDb) {
        try {
            DescribeTableRequest request = DescribeTableRequest.builder()
                    .tableName(TABLE_NAME)
                    .build();

            DescribeTableResponse response = dynamoDb.describeTable(request);
            System.out.println("✅ Table exists: " + TABLE_NAME);
            System.out.println("   Status: " + response.table().tableStatus());
            System.out.println();
        } catch (ResourceNotFoundException e) {
            System.err.println("❌ Table does not exist: " + TABLE_NAME);
            System.err.println("   Please run SetupLocalDB.java first to create the table.");
            System.exit(1);
        }
    }

    /**
     * Ask if user wants to clear existing data
     */
    private static boolean shouldClearExistingData() {
        // For automation, default to clearing
        // In interactive mode, you could prompt the user
        return true;
    }

    /**
     * Clear all existing data from the table
     */
    private static void clearExistingData(DynamoDbClient dynamoDb) {
        System.out.println("🧹 Clearing existing data...");

        try {
            // Scan all items
            ScanRequest scanRequest = ScanRequest.builder()
                    .tableName(TABLE_NAME)
                    .build();

            ScanResponse scanResponse = dynamoDb.scan(scanRequest);

            // Delete each item
            int deleted = 0;
            for (Map<String, AttributeValue> item : scanResponse.items()) {
                String alertId = item.get("alertId").s();

                DeleteItemRequest deleteRequest = DeleteItemRequest.builder()
                        .tableName(TABLE_NAME)
                        .key(Map.of("alertId", AttributeValue.builder().s(alertId).build()))
                        .build();

                dynamoDb.deleteItem(deleteRequest);
                deleted++;
            }

            System.out.println("   Deleted " + deleted + " existing alerts");
            System.out.println();

        } catch (Exception e) {
            System.err.println("⚠️  Warning: Could not clear existing data: " + e.getMessage());
        }
    }

    /**
     * Create test alerts with various scenarios
     */
    private static List<TestAlert> createTestAlerts() {
        List<TestAlert> alerts = new ArrayList<>();

        // Test User 1: Multiple alerts
        alerts.add(new TestAlert(
                "123456789",
                "bitcoin",
                new BigDecimal("30000.00"),
                new BigDecimal("50000.00"),
                "Bitcoin price range alert for user 1"
        ));

        alerts.add(new TestAlert(
                "123456789",
                "ethereum",
                new BigDecimal("2000.00"),
                new BigDecimal("4000.00"),
                "Ethereum price range alert for user 1"
        ));

        alerts.add(new TestAlert(
                "123456789",
                "cardano",
                new BigDecimal("0.50"),
                new BigDecimal("1.50"),
                "Cardano price range alert for user 1"
        ));

        // Test User 2: Different cryptocurrencies
        alerts.add(new TestAlert(
                "987654321",
                "solana",
                new BigDecimal("50.00"),
                new BigDecimal("150.00"),
                "Solana price range alert for user 2"
        ));

        alerts.add(new TestAlert(
                "987654321",
                "dogecoin",
                new BigDecimal("0.10"),
                new BigDecimal("0.30"),
                "Dogecoin price range alert for user 2"
        ));

        // Test User 3: Edge cases
        alerts.add(new TestAlert(
                "555555555",
                "bitcoin",
                new BigDecimal("1.00"),
                new BigDecimal("100000.00"),
                "Wide range Bitcoin alert (likely to trigger)"
        ));

        alerts.add(new TestAlert(
                "555555555",
                "ripple",
                new BigDecimal("0.40"),
                new BigDecimal("0.60"),
                "Narrow range Ripple alert"
        ));

        // Test User 4: Various price points
        alerts.add(new TestAlert(
                "111111111",
                "polkadot",
                new BigDecimal("5.00"),
                new BigDecimal("10.00"),
                "Polkadot price range alert"
        ));

        alerts.add(new TestAlert(
                "111111111",
                "chainlink",
                new BigDecimal("10.00"),
                new BigDecimal("20.00"),
                "Chainlink price range alert"
        ));

        alerts.add(new TestAlert(
                "111111111",
                "avalanche-2",
                new BigDecimal("20.00"),
                new BigDecimal("40.00"),
                "Avalanche price range alert"
        ));

        return alerts;
    }

    /**
     * Insert an alert into DynamoDB
     */
    private static void insertAlert(DynamoDbClient dynamoDb, TestAlert testAlert) {
        String alertId = UUID.randomUUID().toString();

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("alertId", AttributeValue.builder().s(alertId).build());
        item.put("chatId", AttributeValue.builder().s(testAlert.chatId).build());
        item.put("symbol", AttributeValue.builder().s(testAlert.symbol).build());
        item.put("targetPriceMin", AttributeValue.builder().n(testAlert.minPrice.toPlainString()).build());
        item.put("targetPriceMax", AttributeValue.builder().n(testAlert.maxPrice.toPlainString()).build());
        item.put("createdAt", AttributeValue.builder().s(String.valueOf(System.currentTimeMillis())).build());
        item.put("status", AttributeValue.builder().s("ACTIVE").build());

        PutItemRequest putRequest = PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(item)
                .build();

        dynamoDb.putItem(putRequest);
    }

    /**
     * Display summary of created data
     */
    private static void displaySummary(DynamoDbClient dynamoDb) {
        System.out.println("📊 Data Summary:");
        System.out.println("================");

        // Scan all items
        ScanRequest scanRequest = ScanRequest.builder()
                .tableName(TABLE_NAME)
                .build();

        ScanResponse scanResponse = dynamoDb.scan(scanRequest);

        // Group by user
        Map<String, List<Map<String, AttributeValue>>> alertsByUser = new HashMap<>();
        for (Map<String, AttributeValue> item : scanResponse.items()) {
            String chatId = item.get("chatId").s();
            alertsByUser.computeIfAbsent(chatId, k -> new ArrayList<>()).add(item);
        }

        // Display by user
        for (Map.Entry<String, List<Map<String, AttributeValue>>> entry : alertsByUser.entrySet()) {
            String chatId = entry.getKey();
            List<Map<String, AttributeValue>> alerts = entry.getValue();

            System.out.println();
            System.out.println("👤 User (ChatID: " + chatId + ") - " + alerts.size() + " alerts:");

            for (Map<String, AttributeValue> alert : alerts) {
                String symbol = alert.get("symbol").s().toUpperCase();
                String minPrice = alert.get("targetPriceMin").n();
                String maxPrice = alert.get("targetPriceMax").n();
                String alertId = alert.get("alertId").s().substring(0, 8);

                System.out.println("   • " + symbol + ": $" + minPrice + " - $" + maxPrice +
                        " (ID: " + alertId + "...)");
            }
        }

        System.out.println();
        System.out.println("Total alerts: " + scanResponse.items().size());
    }

    /**
     * Show instructions for testing
     */
    private static void showTestingInstructions() {
        System.out.println();
        System.out.println("🧪 Testing Instructions:");
        System.out.println("=======================");
        System.out.println();
        System.out.println("1. Test Price Checker Lambda:");
        System.out.println("   sam local invoke PriceCheckFunction --event events/scheduled-event.json");
        System.out.println();
        System.out.println("2. Test Webhook Handler (list alerts):");
        System.out.println("   Create a test event in events/telegram-list.json with chatId: 123456789");
        System.out.println("   sam local invoke TelegramWebhookFunction --event events/telegram-list.json");
        System.out.println();
        System.out.println("3. View Data in DynamoDB:");
        System.out.println("   aws dynamodb scan --table-name price-alerts-local \\");
        System.out.println("     --endpoint-url http://localhost:8000");
        System.out.println();
        System.out.println("4. Test Specific User's Alerts:");
        System.out.println("   aws dynamodb query --table-name price-alerts-local \\");
        System.out.println("     --index-name ChatIdIndex \\");
        System.out.println("     --key-condition-expression \"chatId = :chatId\" \\");
        System.out.println("     --expression-attribute-values '{\":chatId\":{\"S\":\"123456789\"}}' \\");
        System.out.println("     --endpoint-url http://localhost:8000");
        System.out.println();
        System.out.println("5. Run in IntelliJ:");
        System.out.println("   Right-click TelegramPriceBot.java → Run");
        System.out.println("   (Make sure DYNAMODB_ENDPOINT=http://localhost:8000 is set)");
        System.out.println();
        System.out.println("Test Users:");
        System.out.println("• ChatID 123456789: 3 alerts (Bitcoin, Ethereum, Cardano)");
        System.out.println("• ChatID 987654321: 2 alerts (Solana, Dogecoin)");
        System.out.println("• ChatID 555555555: 2 alerts (Bitcoin wide range, Ripple)");
        System.out.println("• ChatID 111111111: 3 alerts (Polkadot, Chainlink, Avalanche)");
    }

    /**
     * Test alert data class
     */
    private static class TestAlert {
        String chatId;
        String symbol;
        BigDecimal minPrice;
        BigDecimal maxPrice;
        String description;

        TestAlert(String chatId, String symbol, BigDecimal minPrice,
                  BigDecimal maxPrice, String description) {
            this.chatId = chatId;
            this.symbol = symbol;
            this.minPrice = minPrice;
            this.maxPrice = maxPrice;
            this.description = description;
        }
     }
}
