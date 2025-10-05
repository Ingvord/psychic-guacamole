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
        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(alertsTable)
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
        return response.items().stream()
                .map(PriceAlert::fromDynamoItem).toList();
    }

    public List<PriceAlert> findByStatus(String status) {
        QueryRequest request = QueryRequest.builder()
                .tableName(alertsTable)
                .indexName("StatusIndex") // Need to create this GSI
                .keyConditionExpression("#status = :status")
                .expressionAttributeNames(Map.of("#status", "status"))
                .expressionAttributeValues(Map.of(
                        ":status", AttributeValue.builder().s(status).build()
                ))
                .build();

        return dynamoDb.query(request).items().stream()
                .map(PriceAlert::fromDynamoItem).toList();
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
