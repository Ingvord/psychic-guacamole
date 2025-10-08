package ru.ingvord.trading.service;

import com.amazonaws.services.lambda.runtime.Context;
import ru.ingvord.trading.repository.AlertRepository;
import ru.ingvord.trading.repository.PriceAlert;
import ru.ingvord.trading.telegram.TelegramBotMessages;
import ru.ingvord.trading.telegram.TelegramUpdate;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ru.ingvord.trading.repository.AlertRepository.ALERT_STATUS_ACTIVE;
import static ru.ingvord.trading.telegram.TelegramBotMessages.*;
import static ru.ingvord.trading.telegram.TelegramBotMessages.INVALID_PRICE;

public class TelegramBotCommandService {
    // Command patterns
    private static final Pattern ALERT_PATTERN = Pattern.compile("/alert\\s+(\\w+)\\s+([\\d.]+)\\s+([\\d.]+)");
    private static final Pattern PRICE_PATTERN = Pattern.compile("/price\\s+(\\w+)");
    private static final Pattern REMOVE_PATTERN = Pattern.compile("/remove\\s+(\\w+)");
    public static final int MAX_ALERTS_PER_USER = 20;

    private final PriceService priceService;
    private final AlertRepository alertRepository;

    public TelegramBotCommandService(PriceService priceService, AlertRepository alertRepository) {
        this.priceService = priceService;
        this.alertRepository = alertRepository;
    }


    public String execute(TelegramUpdate update, Context context) {
        // Handle the command
        return handleCommand(update.chatId, update.text, context);
    };

    private String handleCommand(long chatId, String text, Context context) {
        if (text.startsWith("/start")) {
            return TelegramBotMessages.WELCOME;
        } else if (text.startsWith("/help")) {
            return TelegramBotMessages.HELP;
        } else if (text.startsWith("/tickers")) {
            return handleTickersCommand();
        } else if (text.startsWith("/alert")) {
            return handleAlertCommand(chatId, text, context);
        } else if (text.startsWith("/list")) {
            return handleListCommand(chatId, context);
        } else if (text.startsWith("/remove")) {
            return handleRemoveCommand(chatId, text, context);
        } else if (text.startsWith("/clear")) {
            return handleClearCommand(chatId, context);
        } else if (text.startsWith("/price")) {
            return handlePriceCommand(text, context);
        } else if (text.startsWith("/")) {
            return "Unknown command. Type /help to see available commands.";
        }

        return null; // Don't respond to non-commands
    }

    private String handleTickersCommand() {
        return "💎 **Supported Cryptocurrencies**\n\n" +
                TickerMapper.getAllTickersMessage() +
                "\n\n💡 **Usage:**\n" +
                "• `/price BTC` - Check Bitcoin price\n" +
                "• `/alert ETH 2000 3000` - Set Ethereum alert";
    }

    private String handleAlertCommand(long chatId, String text, Context context) {
        Matcher matcher = ALERT_PATTERN.matcher(text);
        if (!matcher.matches()) {
            return TelegramBotMessages.INVALID_ALERT;
        }

        // Check user's existing alert count
        try {
            List<PriceAlert> existingAlerts = getUserAlerts(chatId);
            if (existingAlerts.size() >= MAX_ALERTS_PER_USER) {
                return String.format(
                        "❌ **Alert Limit Reached**\n\n" +
                                "You have %d/%d alerts (maximum reached).\n\n" +
                                "Please remove some alerts before creating new ones:\n" +
                                "• Use `/list` to see your alerts\n" +
                                "• Use `/remove <alert_id>` to remove specific alerts\n" +
                                "• Use `/clear` to remove all alerts",
                        existingAlerts.size(), MAX_ALERTS_PER_USER
                );
            }
        } catch (Exception e) {
            context.getLogger().log("Error checking alert count: " + e.getMessage());
            // Continue anyway - better to allow than block on error
        }


        String symbol = matcher.group(1).toLowerCase();
        BigDecimal minPrice;
        BigDecimal maxPrice;
        try {
            minPrice = new BigDecimal(matcher.group(2));
            maxPrice = new BigDecimal(matcher.group(3));
        } catch (NumberFormatException e) {
            return "❌ Invalid price format! Use numbers like: 40000 or 40000.50";
        }


        if (minPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return "❌ Minimum price must be greater than zero!";
        }

        if (maxPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return "❌ Maximum price must be greater than zero!";
        }

        if (minPrice.compareTo(maxPrice) >= 0) {
            return "❌ Minimum price must be less than maximum price!";
        }

        // Sanity check: reasonable price range
        BigDecimal MAX_REASONABLE_PRICE = new BigDecimal("10000000"); // $10M per coin
        if (minPrice.compareTo(MAX_REASONABLE_PRICE) > 0 ||
                maxPrice.compareTo(MAX_REASONABLE_PRICE) > 0) {
            return "❌ Price values seem unreasonably high. Please check your input.";
        }

        // Sanity check: min/max shouldn't be too close (at least 1% difference)
        BigDecimal difference = maxPrice.subtract(minPrice);
        BigDecimal onePercent = minPrice.multiply(new BigDecimal("0.01"));
        if (difference.compareTo(onePercent) < 0) {
            return "❌ Price range too narrow! Min and max should differ by at least 1%.";
        }

        try {
            // Verify symbol exists by checking current price
            double currentPrice = getCurrentPrice(symbol);

            // Create alert
            String alertId = UUID.randomUUID().toString();
            createAlert(alertId, chatId, symbol, minPrice, maxPrice);

            return String.format(ALERT_CREATED,
                    symbol.toUpperCase(), currentPrice, minPrice, maxPrice, alertId.substring(0, 8),
                    symbol.toUpperCase());

        } catch (Exception e) {
            context.getLogger().log("Error creating alert: " + e.getMessage());
            return "❌ Failed to create alert. Please check the symbol name and try again.\n\n" +
                    "Popular symbols: bitcoin, ethereum, cardano, solana, dogecoin";
        }
    }

    private String handleListCommand(long chatId, Context context) {
        try {
            List<PriceAlert> alerts = getUserAlerts(chatId);

            if (alerts.isEmpty()) {
                return NO_ALERTS;
            }

            StringBuilder response = new StringBuilder("📊 **Your Active Alerts:**\n\n");

            for (PriceAlert alert : alerts) {
                String alertId = alert.alertId();
                String symbol = alert.symbol();
                double minPrice = alert.targetPriceMin().doubleValue();
                double maxPrice = alert.targetPriceMax().doubleValue();
                String status = alert.status();

                response.append(String.format(
                        "🔔 **%s**\n" +
                                "   Range: $%.2f - $%.2f\n" +
                                "   ID: `%s`\n\n" +
                                "   Status: `%s`\n\n",
                        symbol, minPrice, maxPrice, alertId.substring(0, 8), status
                ));
            }

            response.append("Use `/remove <alert_id>` to remove specific alerts\n");
            response.append("Use `/clear` to remove all alerts");

            return response.toString();

        } catch (Exception e) {
            context.getLogger().log("Error listing alerts: " + e.getMessage());
            return "❌ Failed to retrieve your alerts. Please try again later.";
        }
    }

    private String handleRemoveCommand(long chatId, String text, Context context) {
        Matcher matcher = REMOVE_PATTERN.matcher(text);
        if (!matcher.matches()) {
            return INVALID_REMOVE;
        }

        String alertIdPrefix = matcher.group(1);

        try {
            List<PriceAlert> alerts = getUserAlerts(chatId);
            String fullAlertId = null;

            // Find matching alert by prefix
            for (PriceAlert alert : alerts) {
                String alertId = alert.alertId();
                if (alertId.startsWith(alertIdPrefix)) {
                    fullAlertId = alertId;
                    break;
                }
            }

            if (fullAlertId == null) {
                return "❌ Alert not found. Use `/list` to see your active alerts.";
            }

            removeAlert(fullAlertId);

            return "✅ Alert removed successfully!";

        } catch (Exception e) {
            context.getLogger().log("Error removing alert: " + e.getMessage());
            return "❌ Failed to remove alert. Please try again later.";
        }
    }

    private String handleClearCommand(long chatId, Context context) {
        try {
            List<PriceAlert> alerts = getUserAlerts(chatId);

            if (alerts.isEmpty()) {
                return "📭 You don't have any active alerts to clear.";
            }

            for (PriceAlert alert : alerts) {
                removeAlert(alert.alertId());
            }

            return String.format("✅ Cleared %d alerts successfully!", alerts.size());

        } catch (Exception e) {
            context.getLogger().log("Error clearing alerts: " + e.getMessage());
            return "❌ Failed to clear alerts. Please try again later.";
        }
    }

    private String handlePriceCommand(String text, Context context) {
        Matcher matcher = PRICE_PATTERN.matcher(text);
        if (!matcher.matches()) {
            return INVALID_PRICE;
        }

        String symbol = matcher.group(1).toLowerCase();

        try {
            double currentPrice = getCurrentPrice(symbol);

            return String.format("""
                💰 **Current Price**
                
                **%s:** $%.2f
                
                Want to set an alert? Use:
                `/alert %s <min_price> <max_price>`
                """,
                    symbol.toUpperCase(), currentPrice, symbol);

        } catch (Exception e) {
            context.getLogger().log("Error getting price: " + e.getMessage());
            return "❌ Failed to get price. Please check the symbol name and try again.\n\n" +
                    "Popular symbols: bitcoin, ethereum, cardano, solana, dogecoin";
        }
    }

    private double getCurrentPrice(String symbol) throws IOException, InterruptedException {
        return priceService.getCurrentPrice(symbol).doubleValue();
    }

    private void createAlert(String alertId, long chatId, String symbol, BigDecimal minPrice, BigDecimal maxPrice) {
        alertRepository.save(new PriceAlert(alertId, chatId, symbol, minPrice, maxPrice, System.currentTimeMillis(), ALERT_STATUS_ACTIVE));
    }

    private List<PriceAlert> getUserAlerts(long chatId) {
        return alertRepository.findByChatId(chatId);
    }

    private void removeAlert(String alertId) {
        alertRepository.delete(alertId);
    }


}
