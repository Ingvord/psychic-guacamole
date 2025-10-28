///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS software.amazon.awssdk:dynamodb:2.21.29

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.net.URI;

/**
 * Setup script for bot users table with subscription info
 */
public class SetupUsersTable {

    private static final String ENDPOINT = "http://localhost:8000";
    private static final String TABLE_NAME = "users-local";

    public static void main(String[] args) {
        System.out.println("🗃️  Setting up users DynamoDB table...");
        System.out.println("Endpoint: " + ENDPOINT);
        System.out.println("Table: " + TABLE_NAME);
        System.out.println();

        DynamoDbClient dynamoDb = DynamoDbClient.builder()
                .region(Region.EU_CENTRAL_1)
                .endpointOverride(URI.create(ENDPOINT))
                .build();

        try {
            // Check if table already exists
            try {
                DescribeTableResponse existingTable = dynamoDb.describeTable(
                        DescribeTableRequest.builder()
                                .tableName(TABLE_NAME)
                                .build()
                );
                System.out.println("⚠️  Table already exists!");
                System.out.println("Status: " + existingTable.table().tableStatus());
                System.out.println();
                listTables(dynamoDb);
                return;
            } catch (ResourceNotFoundException e) {
                System.out.println("Creating new table...");
            }

            // Create users table
            CreateTableRequest request = CreateTableRequest.builder()
                    .tableName(TABLE_NAME)
                    .attributeDefinitions(
                            AttributeDefinition.builder()
                                    .attributeName("chatId")
                                    .attributeType(ScalarAttributeType.S)
                                    .build()
                    )
                    .keySchema(
                            KeySchemaElement.builder()
                                    .attributeName("chatId")
                                    .keyType(KeyType.HASH)
                                    .build()
                    )
                    .provisionedThroughput(ProvisionedThroughput.builder()
                            .readCapacityUnits(5L)
                            .writeCapacityUnits(5L)
                            .build())
                    .build();

            CreateTableResponse response = dynamoDb.createTable(request);
            System.out.println("✅ Users table created successfully!");
            System.out.println("Table ARN: " + response.tableDescription().tableArn());
            System.out.println("Status: " + response.tableDescription().tableStatus());
            System.out.println();

            System.out.println("📋 Table Structure:");
            System.out.println("  Primary Key: chatId (String, HASH)");
            System.out.println("  Attributes:");
            System.out.println("    - username: String");
            System.out.println("    - tier: String (free/premium/lifetime)");
            System.out.println("    - subscriptionExpiresAt: Number (epoch seconds)");
            System.out.println("    - createdAt: Number (epoch seconds)");
            System.out.println("    - lastActivityAt: Number (epoch seconds)");
            System.out.println();

        } catch (DynamoDbException e) {
            System.err.println("❌ Error creating table: " + e.getMessage());
            System.err.println("Details: " + e.awsErrorDetails().errorMessage());
            System.exit(1);
        }

        listTables(dynamoDb);
        System.out.println("🎉 Setup complete!");
    }

    private static void listTables(DynamoDbClient dynamoDb) {
        System.out.println("📊 Available tables:");
        ListTablesResponse response = dynamoDb.listTables();
        if (response.tableNames().isEmpty()) {
            System.out.println("  (none)");
        } else {
            response.tableNames().forEach(name -> System.out.println("  • " + name));
        }
        System.out.println();
    }
}
