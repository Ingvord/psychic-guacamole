package ru.ingvord.trading.service;

import java.util.Map;

/**
 * Normalises whatever the user typed into a Bybit symbol.
 *
 * Alerts are stored as typed (btc, BTC/USD, bitcoin, QQQ, ethusdt); this is applied at lookup time,
 * so old alerts keep working. Whether the symbol lives on Bybit linear or spot is decided by {@link Bybit}.
 */
public class TickerMapper {

    /** Legacy names (CoinGecko ids from the first versions of the bot) and tokens Bybit has renamed. */
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("bitcoin", "BTC"),
            Map.entry("ethereum", "ETH"),
            Map.entry("binancecoin", "BNB"),
            Map.entry("solana", "SOL"),
            Map.entry("ripple", "XRP"),
            Map.entry("cardano", "ADA"),
            Map.entry("dogecoin", "DOGE"),
            Map.entry("polkadot", "DOT"),
            Map.entry("chainlink", "LINK"),
            Map.entry("litecoin", "LTC"),
            Map.entry("avalanche-2", "AVAX"),
            Map.entry("tron", "TRX"),
            Map.entry("cosmos", "ATOM"),
            Map.entry("stellar", "XLM"),
            Map.entry("uniswap", "UNI"),
            // renamed on Bybit
            Map.entry("matic", "POL"),
            Map.entry("matic-network", "POL"),
            Map.entry("ftm", "S"),
            Map.entry("fantom", "S"),
            Map.entry("rndr", "RENDER"),
            Map.entry("render-token", "RENDER"));

    private TickerMapper() {}

    /**
     * Examples:
     * - BTC, btc, bitcoin, BTC/USD, BTC/USDT → BTCUSDT
     * - QQQ, qqq → QQQUSDT
     * - ETHUSDT, ETHUSDC → unchanged
     * - matic → POLUSDT
     */
    public static String toBybitSymbol(String input) {
        String raw = input.trim();

        String alias = ALIASES.get(raw.toLowerCase());
        if (alias != null) {
            return alias + "USDT";
        }

        String symbol = raw.toUpperCase();
        if (symbol.endsWith("/USD")) {
            symbol = symbol.substring(0, symbol.length() - "/USD".length());
        }
        symbol = symbol.replaceAll("[/\\-_]", "");

        if (symbol.endsWith("USDT") || symbol.endsWith("USDC")) {
            return symbol;
        }

        alias = ALIASES.get(symbol.toLowerCase());
        return (alias != null ? alias : symbol) + "USDT";
    }
}
