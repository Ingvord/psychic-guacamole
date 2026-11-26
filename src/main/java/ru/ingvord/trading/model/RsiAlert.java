package ru.ingvord.trading.model;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.Map;

public record RsiAlert(
        String alertId,
        long chatId,
        String symbol,
        String interval,   // user timeframe: 15m, 30m, 1h, 2h, 4h, 1d, 1w
        int period,
        String seriesType, // close | open | high | low
        String lastZone,   // NORMAL | OVERBOUGHT | OVERSOLD
        long createdAt,
        String status
) {
    public static final String ZONE_NORMAL = "NORMAL";
    public static final String ZONE_OVERBOUGHT = "OVERBOUGHT";
    public static final String ZONE_OVERSOLD = "OVERSOLD";

    public static final java.math.BigDecimal OVERBOUGHT_LEVEL = new java.math.BigDecimal("70");
    public static final java.math.BigDecimal OVERSOLD_LEVEL = new java.math.BigDecimal("30");

    public static String zoneOf(java.math.BigDecimal rsi) {
        if (rsi.compareTo(OVERBOUGHT_LEVEL) >= 0) return ZONE_OVERBOUGHT;
        if (rsi.compareTo(OVERSOLD_LEVEL) <= 0) return ZONE_OVERSOLD;
        return ZONE_NORMAL;
    }

    public static RsiAlert fromDynamoItem(Map<String, AttributeValue> item) {
        return new RsiAlert(
                item.get("alertId").s(),
                Long.parseLong(item.get("chatId").s()),
                item.get("symbol").s().toLowerCase(),
                item.get("interval").s(),
                Integer.parseInt(item.get("period").n()),
                item.get("seriesType").s(),
                item.get("lastZone").s(),
                Long.parseLong(item.get("createdAt").s()),
                item.get("status").s());
    }

    public Map<String, AttributeValue> toDynamoItem() {
        var item = new HashMap<String, AttributeValue>();
        item.put("alertId", AttributeValue.builder().s(alertId).build());
        item.put("chatId", AttributeValue.builder().s(String.valueOf(chatId)).build());
        item.put("symbol", AttributeValue.builder().s(symbol).build());
        item.put("interval", AttributeValue.builder().s(interval).build());
        item.put("period", AttributeValue.builder().n(String.valueOf(period)).build());
        item.put("seriesType", AttributeValue.builder().s(seriesType).build());
        item.put("lastZone", AttributeValue.builder().s(lastZone).build());
        item.put("createdAt", AttributeValue.builder().s(String.valueOf(createdAt)).build());
        item.put("status", AttributeValue.builder().s(status).build());
        return item;
    }
}
