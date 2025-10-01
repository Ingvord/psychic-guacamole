///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS software.amazon.awssdk:dynamodb:2.21.29

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.net.URI;

public class SetupLocalDBSchema {

    private static final String ENDPOINT = "http://localhost:8000";
    private static final String TABLE_NAME = "price-alerts-local";

    public static void main(String[] args) {
        System.out.println("🗃️  Setting up local DynamoDB table...");
        System.out.println("Endpoint: " + ENDPOINT);
        System.out.println("Table: " + TABLE_NAME);
        System.out.println();

        // Connect to local DynamoDB
        DynamoDbClient dynamoDb = DynamoDbClient.builder()
                .region(Region.US_EAST_1)
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
                // Table doesn't exist, proceed to create
                System.out.println("Creating new table...");
            }

            // Create table
            CreateTableRequest request = CreateTableRequest.builder()
                    .tableName(TABLE_NAME)
                    .attributeDefinitions(
                            AttributeDefinition.builder()
                                    .attributeName("alertId")
                                    .attributeType(ScalarAttributeType.S)
                                    .build(),
                            AttributeDefinition.builder()
                                    .attributeName("chatId")
                                    .attributeType(ScalarAttributeType.S)
                                    .build()
                    )
                    .keySchema(
                            KeySchemaElement.builder()
                                    .attributeName("alertId")
                                    .keyType(KeyType.HASH)
                                    .build()
                    )
                    .globalSecondaryIndexes(
                            GlobalSecondaryIndex.builder()
                                    .indexName("ChatIdIndex")
                                    .keySchema(
                                            KeySchemaElement.builder()
                                                    .attributeName("chatId")
                                                    .keyType(KeyType.HASH)
                                                    .build()
                                    )
                                    .projection(Projection.builder()
                                            .projectionType(ProjectionType.ALL)
                                            .build())
                                    .provisionedThroughput(ProvisionedThroughput.builder()
                                            .readCapacityUnits(5L)
                                            .writeCapacityUnits(5L)
                                            .build())
                                    .build()
                    )
                    .provisionedThroughput(ProvisionedThroughput.builder()
                            .readCapacityUnits(5L)
                            .writeCapacityUnits(5L)
                            .build())
                    .build();

            CreateTableResponse response = dynamoDb.createTable(request);
            System.out.println("✅ Table created successfully!");
            System.out.println("Table ARN: " + response.tableDescription().tableArn());
            System.out.println("Status: " + response.tableDescription().tableStatus());
            System.out.println();

            // Show table details
            System.out.println("📋 Table Structure:");
            System.out.println("  Primary Key: alertId (String, HASH)");
            System.out.println("  Global Secondary Index: ChatIdIndex");
            System.out.println("    - Key: chatId (String, HASH)");
            System.out.println("    - Projection: ALL");
            System.out.println();

        } catch (DynamoDbException e) {
            System.err.println("❌ Error creating table: " + e.getMessage());
            System.err.println("Details: " + e.awsErrorDetails().errorMessage());
            System.exit(1);
        }

        // List all tables
        listTables(dynamoDb);

        System.out.println("🎉 Setup complete! You can now run your bot.");
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
