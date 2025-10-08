package ru.ingvord.trading.repository;

import ru.ingvord.trading.telegram.TelegramPriceBot;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.math.BigDecimal;
import java.util.*;

public class AlertRepository {

    public static final String ALERT_STATUS_ACTIVE = "ACTIVE";
    public static final String ALERT_STATUS_TRIGGERED = "TRIGGERED";

    private final String alertsTable;
    private final DynamoDbClient dynamoDb;


    public AlertRepository(String alertsTable, DynamoDbClient dynamoDb) {
        this.alertsTable = alertsTable;
        this.dynamoDb = dynamoDb;
    }

    public void save(PriceAlert alert){
        PutItemRequest putRequest = PutItemRequest.builder()
                .tableName(alertsTable)
                .item(alert.toDynamoItem())
                .build();

        dynamoDb.putItem(putRequest);
    }

    public void updateStatus(String alertId, String status){
        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName(alertsTable)
                .key(Map.of("alertId", AttributeValue.builder().s(alertId).build()))
                .updateExpression("SET status = :status")
                .expressionAttributeValues(Map.of(
                        ":status", AttributeValue.builder().s(status).build()
                ))
                .build();

        dynamoDb.updateItem(updateRequest);
    }

    public List<PriceAlert> findAll(){
        ScanRequest scanRequest = ScanRequest.builder()
                .tableName(alertsTable)
                .filterExpression("attribute_exists(chatId) AND attribute_exists(symbol)")
                .build();

        ScanResponse response = dynamoDb.scan(scanRequest);
        return response.items().stream()
                .map(PriceAlert::fromDynamoItem).toList();
    }

    public List<PriceAlert> findByChatId(long chatId){
        List<PriceAlert> allAlerts = new ArrayList<>();
        Map<String, AttributeValue> lastEvaluatedKey = null;

        do {
            QueryRequest.Builder builder = QueryRequest.builder()
                    .tableName(alertsTable)
                    .indexName("ChatIdIndex")
                    .keyConditionExpression("chatId = :chatId")
                    .filterExpression("attribute_not_exists(#status) OR #status = :status")
                    .expressionAttributeNames(Map.of("#status", "status"))
                    .expressionAttributeValues(Map.of(
                            ":chatId", AttributeValue.builder().s(String.valueOf(chatId)).build(),
                            ":status", AttributeValue.builder().s("ACTIVE").build()
                    ));

            if (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty()) {
                builder.exclusiveStartKey(lastEvaluatedKey);
            }

            QueryResponse response = dynamoDb.query(builder.build());
            allAlerts.addAll(response.items().stream()
                    .map(PriceAlert::fromDynamoItem)
                    .toList());

            lastEvaluatedKey = response.lastEvaluatedKey();

        } while (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty());

        return allAlerts;
    }

    public List<PriceAlert> findByStatus(String status) {
        List<PriceAlert> allAlerts = new ArrayList<>();
        Map<String, AttributeValue> lastEvaluatedKey = null;

        do {
            QueryRequest.Builder builder = QueryRequest.builder()
                    .tableName(alertsTable)
                    .indexName("StatusIndex")
                    .keyConditionExpression("#status = :status")
                    .expressionAttributeNames(Map.of("#status", "status"))
                    .expressionAttributeValues(Map.of(
                            ":status", AttributeValue.builder().s(status).build()
                    ));

            // Add pagination key if exists
            if (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty()) {
                builder.exclusiveStartKey(lastEvaluatedKey);
            }

            QueryResponse response = dynamoDb.query(builder.build());

            // Add results to list
            allAlerts.addAll(response.items().stream()
                    .map(PriceAlert::fromDynamoItem)
                    .toList());

            // Get next page key
            lastEvaluatedKey = response.lastEvaluatedKey();

        } while (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty());

        return allAlerts;
    }

    public void delete(String alertId){
        DeleteItemRequest deleteRequest = DeleteItemRequest.builder()
                .tableName(alertsTable)
                .key(Map.of("alertId", AttributeValue.builder().s(alertId).build()))
                .build();

        dynamoDb.deleteItem(deleteRequest);
    }

    public void deleteBatch(List<String> alertIds){

    }

}
