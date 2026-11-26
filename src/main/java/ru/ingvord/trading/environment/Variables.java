package ru.ingvord.trading.environment;

public class Variables {
    public static final String TELEGRAM_BOT_TOKEN = System.getenv("TELEGRAM_BOT_TOKEN");
    public static final String WEBHOOK_SECRET_TOKEN = System.getenv("WEBHOOK_SECRET_TOKEN");
    public static final String ALERTS_TABLE = System.getenv("ALERTS_TABLE");;
    public static final String DYNAMODB_ENDPOINT = System.getenv("DYNAMODB_ENDPOINT");
}
