package ru.ingvord.trading.fixtures;

/**
 * Settings for tests that talk to the real Telegram bot (-Ptelegram / -Pe2e).
 * Offline runs use placeholders; nothing is read from or written to disk.
 */
public final class TelegramEnv {
    private TelegramEnv() {}

    public static boolean live() {
        return Boolean.getBoolean("telegram.live");
    }

    public static String token() {
        return live() ? require("TELEGRAM_BOT_TOKEN") : "test-token";
    }

    public static long chatId() {
        return live() ? Long.parseLong(require("TEST_CHAT_ID")) : 1001L;
    }

    private static String require(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("telegram.live=true but env " + name + " is not set");
        }
        return value;
    }
}
