package ru.ingvord.trading.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Maps trading tickers to CoinGecko IDs
 * Allows users to use familiar ticker symbols (BTC, ETH)
 * while using CoinGecko API under the hood
 */
public class TickerMapper {

    private static final Map<String, CoinInfo> TICKER_TO_COINGECKO = new HashMap<>();

    static {
        // Major cryptocurrencies
        add("BTC", "bitcoin", "Bitcoin");
        add("ETH", "ethereum", "Ethereum");
        add("USDT", "tether", "Tether");
        add("BNB", "binancecoin", "Binance Coin");
        add("SOL", "solana", "Solana");
        add("XRP", "ripple", "XRP");
        add("USDC", "usd-coin", "USD Coin");
        add("ADA", "cardano", "Cardano");
        add("AVAX", "avalanche-2", "Avalanche");
        add("DOGE", "dogecoin", "Dogecoin");
        add("DOT", "polkadot", "Polkadot");
        add("MATIC", "matic-network", "Polygon");
        add("SHIB", "shiba-inu", "Shiba Inu");
        add("TRX", "tron", "TRON");
        add("WBTC", "wrapped-bitcoin", "Wrapped Bitcoin");
        add("LINK", "chainlink", "Chainlink");
        add("TON", "the-open-network", "Toncoin");
        add("UNI", "uniswap", "Uniswap");
        add("ATOM", "cosmos", "Cosmos");
        add("LTC", "litecoin", "Litecoin");
        add("XMR", "monero", "Monero");
        add("BCH", "bitcoin-cash", "Bitcoin Cash");
        add("ETC", "ethereum-classic", "Ethereum Classic");
        add("NEAR", "near", "NEAR Protocol");
        add("XLM", "stellar", "Stellar");
        add("APT", "aptos", "Aptos");
        add("ALGO", "algorand", "Algorand");
        add("ARB", "arbitrum", "Arbitrum");
        add("FIL", "filecoin", "Filecoin");
        add("ICP", "internet-computer", "Internet Computer");
        add("VET", "vechain", "VeChain");
        add("HBAR", "hedera-hashgraph", "Hedera");
        add("OP", "optimism", "Optimism");
        add("INJ", "injective-protocol", "Injective");
        add("GRT", "the-graph", "The Graph");
        add("AAVE", "aave", "Aave");
        add("MKR", "maker", "Maker");
        add("SNX", "havven", "Synthetix");
        add("THETA", "theta-token", "Theta Network");
        add("FTM", "fantom", "Fantom");
        add("SAND", "the-sandbox", "The Sandbox");
        add("MANA", "decentraland", "Decentraland");
        add("AXS", "axie-infinity", "Axie Infinity");

        // Stablecoins
        add("DAI", "dai", "Dai");
        add("BUSD", "binance-usd", "Binance USD");
        add("TUSD", "true-USD", "TrueUSD");
        add("USDD", "usdd", "USDD");
        add("FRAX", "frax", "Frax");
        add("GUSD", "gemini-dollar", "Gemini Dollar");

        // DeFi tokens
        add("CAKE", "pancakeswap-token", "PancakeSwap");
        add("SUSHI", "sushi", "SushiSwap");
        add("COMP", "compound-governance-token", "Compound");
        add("CRV", "curve-dao-token", "Curve DAO Token");
        add("BAL", "balancer", "Balancer");
        add("YFI", "yearn-finance", "Yearn Finance");

        // Layer 2 & Scaling
        add("IMX", "immutable-x", "Immutable X");
        add("LRC", "loopring", "Loopring");
        add("ZK", "zksync", "zkSync");

        // Gaming & Metaverse
        add("ENJ", "enjincoin", "Enjin Coin");
        add("GALA", "gala", "Gala");
        add("APE", "apecoin", "ApeCoin");

        // Other Popular
        add("RNDR", "render-token", "Render");
        add("RUNE", "thorchain", "THORChain");
        add("QNT", "quant-network", "Quant");
        add("FTT", "ftx-token", "FTX Token");
    }

    private static void add(String ticker, String coingeckoId, String fullName) {
        TICKER_TO_COINGECKO.put(ticker.toUpperCase(), new CoinInfo(ticker, coingeckoId, fullName));
    }

    /**
     * Convert user input to CoinGecko ID
     * Supports both ticker (BTC) and CoinGecko ID (bitcoin)
     */
    public static String toCoinGeckoId(String input) {
        String upperInput = input.toUpperCase();

        // Check if it's a known ticker
        if (TICKER_TO_COINGECKO.containsKey(upperInput)) {
            return TICKER_TO_COINGECKO.get(upperInput).coingeckoId();
        }

        // Check if it's already a CoinGecko ID
        String lowerInput = input.toLowerCase();
        Optional<CoinInfo> byId = TICKER_TO_COINGECKO.values().stream()
                .filter(info -> info.coingeckoId().equals(lowerInput))
                .findFirst();

        if (byId.isPresent()) {
            return lowerInput;
        }

        // Return as-is and let CoinGecko API handle it
        return lowerInput;
    }

    /**
     * Get display information for a symbol
     */
    public static CoinInfo getCoinInfo(String input) {
        String upperInput = input.toUpperCase();

        // Try ticker first
        if (TICKER_TO_COINGECKO.containsKey(upperInput)) {
            return TICKER_TO_COINGECKO.get(upperInput);
        }

        // Try CoinGecko ID
        String lowerInput = input.toLowerCase();
        Optional<CoinInfo> byId = TICKER_TO_COINGECKO.values().stream()
                .filter(info -> info.coingeckoId().equals(lowerInput))
                .findFirst();

        return byId.orElse(new CoinInfo(
                input.toUpperCase(),
                input.toLowerCase(),
                capitalize(input)
        ));
    }

    /**
     * Check if a ticker/symbol is supported
     */
    public static boolean isSupported(String input) {
        String upperInput = input.toUpperCase();
        if (TICKER_TO_COINGECKO.containsKey(upperInput)) {
            return true;
        }

        String lowerInput = input.toLowerCase();
        return TICKER_TO_COINGECKO.values().stream()
                .anyMatch(info -> info.coingeckoId().equals(lowerInput));
    }

    /**
     * Get all supported tickers for help message
     */
    public static String getSupportedTickersMessage() {
        StringBuilder sb = new StringBuilder("**Popular Tickers:**\n\n");

        String[] featured = {"BTC", "ETH", "SOL", "ADA", "DOGE", "XRP",
                "MATIC", "AVAX", "DOT", "LINK", "UNI", "ATOM"};

        for (String ticker : featured) {
            CoinInfo info = TICKER_TO_COINGECKO.get(ticker);
            if (info != null) {
                sb.append(String.format("• **%s** - %s\n", ticker, info.fullName()));
            }
        }

        sb.append("\n_...and ").append(TICKER_TO_COINGECKO.size() - featured.length)
                .append(" more!_\n\n");
        sb.append("💡 Use `/tickers` to see the full list");

        return sb.toString();
    }

    /**
     * Get all tickers in categories
     */
    public static String getAllTickersMessage() {
        StringBuilder sb = new StringBuilder();

        sb.append("💎 **All Supported Cryptocurrencies**\n\n");

        // Top 10
        sb.append("**🏆 Top Cryptocurrencies:**\n");
        String[] top = {"BTC", "ETH", "USDT", "BNB", "SOL", "XRP", "USDC", "ADA", "DOGE", "TRX"};
        for (String ticker : top) {
            CoinInfo info = TICKER_TO_COINGECKO.get(ticker);
            if (info != null) {
                sb.append(String.format("• %s - %s\n", ticker, info.fullName()));
            }
        }

        // DeFi
        sb.append("\n**🔄 DeFi Tokens:**\n");
        String[] defi = {"UNI", "AAVE", "MKR", "SNX", "COMP", "CRV", "CAKE", "SUSHI"};
        for (String ticker : defi) {
            CoinInfo info = TICKER_TO_COINGECKO.get(ticker);
            if (info != null) {
                sb.append(String.format("• %s - %s\n", ticker, info.fullName()));
            }
        }

        // Layer 2
        sb.append("\n**⚡ Layer 2 & Scaling:**\n");
        String[] l2 = {"MATIC", "ARB", "OP", "IMX", "LRC"};
        for (String ticker : l2) {
            CoinInfo info = TICKER_TO_COINGECKO.get(ticker);
            if (info != null) {
                sb.append(String.format("• %s - %s\n", ticker, info.fullName()));
            }
        }

        sb.append(String.format("\n**Total:** %d cryptocurrencies supported", TICKER_TO_COINGECKO.size()));

        return sb.toString();
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    /**
     * Coin information record
     */
    public record CoinInfo(String ticker, String coingeckoId, String fullName) {
        public String displayName() {
            return fullName + " (" + ticker + ")";
        }
    }
}
