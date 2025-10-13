package ru.ingvord.trading.model;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public record PriceAlert(
        String alertId,
        long chatId,
        String symbol,
        BigDecimal targetPriceMin,
        BigDecimal targetPriceMax,
        long createdAt,
        String status //TODO enum
) {
    public static PriceAlert fromDynamoItem(Map<String, AttributeValue> item){
        return new PriceAlert(
                item.get("alertId").s(),
                Long.parseLong(item.get("chatId").s()),
                item.get("symbol").s().toLowerCase(),
                new BigDecimal(item.get("targetPriceMin").n()),
                new BigDecimal(item.get("targetPriceMax").n()),
                Long.parseLong(item.get("createdAt").s().toLowerCase()),
                item.get("status").s().toLowerCase());
    }

    public Map<String, AttributeValue> toDynamoItem() {
        var item = new HashMap<String, AttributeValue>();
        item.put("alertId", AttributeValue.builder().s(alertId).build());
        item.put("chatId", AttributeValue.builder().s(String.valueOf(chatId)).build());
        item.put("symbol", AttributeValue.builder().s(symbol).build());
        item.put("targetPriceMin", AttributeValue.builder().n(String.valueOf(targetPriceMin)).build());
        item.put("targetPriceMax", AttributeValue.builder().n(String.valueOf(targetPriceMax)).build());
        item.put("createdAt", AttributeValue.builder().s(String.valueOf(System.currentTimeMillis())).build());
        item.put("status", AttributeValue.builder().s(status).build());
        return item;
    }
}
