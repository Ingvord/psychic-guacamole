package ru.ingvord.trading.service;

import com.amazonaws.services.lambda.runtime.Context;
import ru.ingvord.trading.repository.AlertRepository;
import ru.ingvord.trading.model.PriceAlert;
import ru.ingvord.trading.model.RsiAlert;
import ru.ingvord.trading.telegram.TelegramBotMessages;
import ru.ingvord.trading.telegram.TelegramUpdate;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ru.ingvord.trading.repository.AlertRepository.ALERT_STATUS_ACTIVE;
import static ru.ingvord.trading.repository.AlertRepository.ALERT_STATUS_RSI_ACTIVE;
import static ru.ingvord.trading.telegram.TelegramBotMessages.*;
import static ru.ingvord.trading.telegram.TelegramBotMessages.INVALID_PRICE;

public class TelegramBotCommandService {
    // Command patterns
    private static final Pattern ALERT_PATTERN = Pattern.compile("/alert\\s+(\\w+)\\s+([\\d.]+)\\s+([\\d.]+)");
    private static final Pattern PRICE_PATTERN = Pattern.compile("/price\\s+(\\w+)");
    private static final Pattern REMOVE_PATTERN = Pattern.compile("/remove\\s+(\\w+)");
    private static final Pattern RSI_PATTERN = Pattern.compile(
            "/rsi\\s+(\\w+)\\s+(15m|30m|1h|2h|4h|1d|1w)(?:\\s+(\\d+))?(?:\\s+(close|open|high|low))?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RSI_REMOVE_PATTERN = Pattern.compile("/rsi\\s+rm\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
    public static final int MAX_ALERTS_PER_USER = 20;
    public static final int MAX_RSI_ALERTS_PER_USER = 5;
    public static final int DEFAULT_RSI_PERIOD = 14;

    private final PriceService priceService;
    private final IndicatorService indicatorService;
    private final AlertRepository alertRepository;

    public TelegramBotCommandService(PriceService priceService, IndicatorService indicatorService, AlertRepository alertRepository) {
        this.priceService = priceService;
        this.indicatorService = indicatorService;
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
        } else if (text.startsWith("/rsi")) {
            return handleRsiCommand(chatId, text.trim(), context);
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

    private String handleRsiCommand(long chatId, String text, Context context) {
        if (text.equalsIgnoreCase("/rsi")) {
            return listRsiAlerts(chatId, context);
        }

        Matcher removeMatcher = RSI_REMOVE_PATTERN.matcher(text);
        if (removeMatcher.matches()) {
            return removeRsiAlert(chatId, removeMatcher.group(1), context);
        }

        Matcher matcher = RSI_PATTERN.matcher(text);
        if (!matcher.matches()) {
            return INVALID_RSI;
        }

        String symbol = matcher.group(1).toLowerCase();
        String interval = matcher.group(2).toLowerCase();
        int period = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : DEFAULT_RSI_PERIOD;
        String seriesType = matcher.group(4) != null ? matcher.group(4).toLowerCase() : "close";

        if (period < 2 || period > 100) {
            return "❌ RSI period must be between 2 and 100.";
        }

        try {
            if (alertRepository.findRsiByChatId(chatId).size() >= MAX_RSI_ALERTS_PER_USER) {
                return String.format("❌ RSI alert limit reached (%d). Use `/rsi` to list and `/rsi rm <id>` to remove.",
                        MAX_RSI_ALERTS_PER_USER);
            }

            // Also verifies the symbol and gives the starting zone, so creating inside a zone doesn't fire at once
            BigDecimal rsi = indicatorService.getRsi(symbol, interval, period, seriesType);

            String alertId = UUID.randomUUID().toString();
            alertRepository.saveRsi(new RsiAlert(alertId, chatId, symbol, interval, period, seriesType,
                    RsiAlert.zoneOf(rsi), System.currentTimeMillis(), ALERT_STATUS_RSI_ACTIVE));

            return String.format(RSI_ALERT_CREATED,
                    symbol.toUpperCase(), interval, period, seriesType, rsi, alertId.substring(0, 8));
        } catch (Exception e) {
            context.getLogger().log("Error creating RSI alert: " + e.getMessage());
            return "❌ Failed to create RSI alert. RSI supports crypto pairs listed on Bybit spot (e.g. BTC, ETH, SOL) — please check the symbol and try again.";
        }
    }

    private String listRsiAlerts(long chatId, Context context) {
        try {
            List<RsiAlert> alerts = alertRepository.findRsiByChatId(chatId);
            if (alerts.isEmpty()) {
                return "📭 You have no RSI alerts.\n\nCreate one: `/rsi BTC 4h`";
            }

            StringBuilder response = new StringBuilder("📈 **Your RSI Alerts:**\n\n");
            for (RsiAlert alert : alerts) {
                response.append(String.format("• **%s** %s (period %d, %s) — ID: `%s`\n",
                        alert.symbol().toUpperCase(), alert.interval(), alert.period(),
                        alert.seriesType(), alert.alertId().substring(0, 8)));
            }
            response.append("\nUse `/rsi rm <id>` to remove an alert");
            return response.toString();
        } catch (Exception e) {
            context.getLogger().log("Error listing RSI alerts: " + e.getMessage());
            return "❌ Failed to retrieve your RSI alerts. Please try again later.";
        }
    }

    private String removeRsiAlert(long chatId, String idPrefix, Context context) {
        try {
            for (RsiAlert alert : alertRepository.findRsiByChatId(chatId)) {
                if (alert.alertId().startsWith(idPrefix)) {
                    removeAlert(alert.alertId());
                    return "✅ RSI alert removed successfully!";
                }
            }
            return "❌ RSI alert not found. Use `/rsi` to see your RSI alerts.";
        } catch (Exception e) {
            context.getLogger().log("Error removing RSI alert: " + e.getMessage());
            return "❌ Failed to remove RSI alert. Please try again later.";
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
