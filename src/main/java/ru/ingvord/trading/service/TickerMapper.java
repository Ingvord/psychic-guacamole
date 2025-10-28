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

    private static final Map<String, CoinInfo> CRYPTO_TICKERS = new HashMap<>();
    private static final Map<String, StockInfo>  STOCK_TICKERS = new HashMap<>();

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

        // === STOCKS ===
        // Tech Giants
        addStock("AAPL", "Apple Inc.");
        addStock("MSFT", "Microsoft Corporation");
        addStock("GOOGL", "Alphabet Inc. (Google)");
        addStock("GOOG", "Alphabet Inc. Class C");
        addStock("AMZN", "Amazon.com Inc.");
        addStock("META", "Meta Platforms Inc. (Facebook)");
        addStock("TSLA", "Tesla Inc.");
        addStock("NVDA", "NVIDIA Corporation");
        addStock("AMD", "Advanced Micro Devices");
        addStock("INTC", "Intel Corporation");

        // Finance
        addStock("JPM", "JPMorgan Chase & Co.");
        addStock("BAC", "Bank of America Corp.");
        addStock("WFC", "Wells Fargo & Company");
        addStock("GS", "Goldman Sachs Group Inc.");
        addStock("MS", "Morgan Stanley");
        addStock("V", "Visa Inc.");
        addStock("MA", "Mastercard Inc.");
        addStock("PYPL", "PayPal Holdings Inc.");

        // Consumer
        addStock("WMT", "Walmart Inc.");
        addStock("HD", "Home Depot Inc.");
        addStock("NKE", "Nike Inc.");
        addStock("MCD", "McDonald's Corporation");
        addStock("SBUX", "Starbucks Corporation");
        addStock("DIS", "Walt Disney Company");
        addStock("NFLX", "Netflix Inc.");

        // Automotive
        addStock("F", "Ford Motor Company");
        addStock("GM", "General Motors Company");

        // Healthcare & Pharma
        addStock("JNJ", "Johnson & Johnson");
        addStock("PFE", "Pfizer Inc.");
        addStock("MRNA", "Moderna Inc.");
        addStock("UNH", "UnitedHealth Group Inc.");

        // Energy
        addStock("XOM", "Exxon Mobil Corporation");
        addStock("CVX", "Chevron Corporation");

        // Industrial
        addStock("BA", "Boeing Company");
        addStock("CAT", "Caterpillar Inc.");

        // Indices (for reference)
        addStock("SPY", "SPDR S&P 500 ETF");
        addStock("QQQ", "Invesco QQQ Trust (Nasdaq-100)");
        addStock("DIA", "SPDR Dow Jones Industrial Average ETF");
    }

    private static void add(String ticker, String coingeckoId, String fullName) {
        CRYPTO_TICKERS.put(ticker.toUpperCase(), new CoinInfo(ticker, coingeckoId, fullName));
    }

    private static void addStock(String ticker, String fullName) {
        STOCK_TICKERS.put(ticker.toUpperCase(), new StockInfo(ticker, fullName));
    }

    /**
     * Convert user input to Twelve Data format
     *
     * Examples:
     * - BTC → BTC/USD
     * - AAPL → AAPL
     * - bitcoin → BTC/USD
     */
    public static String toTwelveDataSymbol(String input) {
        String upperInput = input.toUpperCase();

        // Check if it's a known crypto ticker
        if (CRYPTO_TICKERS.containsKey(upperInput)) {
            return upperInput + "/USD"; // Twelve Data crypto format
        }

        // Check if it's a known stock ticker
        if (STOCK_TICKERS.containsKey(upperInput)) {
            return upperInput; // Stocks use ticker as-is
        }

        // Check if it's a crypto name (like "bitcoin")
        String lowerInput = input.toLowerCase();
        Optional<CoinInfo> byCoinGeckoId = CRYPTO_TICKERS.values().stream()
                .filter(info -> info.coinGeckoId().equals(lowerInput))
                .findFirst();

        if (byCoinGeckoId.isPresent()) {
            return byCoinGeckoId.get().ticker() + "/USD";
        }

        // Check if it already has /USD format (already in Twelve Data format)
        if (input.contains("/")) {
            return input;
        }

        // Default: assume it's a stock ticker
        return upperInput;
    }

    /**
     * Convert Twelve Data symbol back to user-friendly format
     *
     * Examples:
     * - BTC/USD → BTC
     * - AAPL → AAPL
     */
    public static String fromTwelveDataSymbol(String twelveDataSymbol) {
        // If it has /USD, it's crypto - remove the /USD part
        if (twelveDataSymbol.endsWith("/USD")) {
            return twelveDataSymbol.replace("/USD", "");
        }

        // Otherwise return as-is (stock ticker)
        return twelveDataSymbol;
    }

    /**
     * Get display information for a symbol
     */
    public static AssetInfo getAssetInfo(String input) {
        String upperInput = input.toUpperCase();

        // Try crypto first
        if (CRYPTO_TICKERS.containsKey(upperInput)) {
            CoinInfo coin = CRYPTO_TICKERS.get(upperInput);
            return new AssetInfo(
                    coin.ticker(),
                    coin.fullName(),
                    AssetType.CRYPTO,
                    coin.fullName() + " (" + coin.ticker() + ")"
            );
        }

        // Try stock
        if (STOCK_TICKERS.containsKey(upperInput)) {
            StockInfo stock = STOCK_TICKERS.get(upperInput);
            return new AssetInfo(
                    stock.ticker(),
                    stock.fullName(),
                    AssetType.STOCK,
                    stock.fullName() + " (" + stock.ticker() + ")"
            );
        }

        // Check crypto by name
        String lowerInput = input.toLowerCase();
        Optional<CoinInfo> byCoinGeckoId = CRYPTO_TICKERS.values().stream()
                .filter(info -> info.coinGeckoId().equals(lowerInput))
                .findFirst();

        if (byCoinGeckoId.isPresent()) {
            CoinInfo coin = byCoinGeckoId.get();
            return new AssetInfo(
                    coin.ticker(),
                    coin.fullName(),
                    AssetType.CRYPTO,
                    coin.fullName() + " (" + coin.ticker() + ")"
            );
        }

        // Unknown - default to stock
        return new AssetInfo(
                upperInput,
                capitalize(input),
                AssetType.STOCK,
                capitalize(input) + " (" + upperInput + ")"
        );
    }

    /**
     * Detect asset type
     */
    public static AssetType detectAssetType(String input) {
        String upperInput = input.toUpperCase();

        if (CRYPTO_TICKERS.containsKey(upperInput)) {
            return AssetType.CRYPTO;
        }

        if (STOCK_TICKERS.containsKey(upperInput)) {
            return AssetType.STOCK;
        }

        // Check by crypto name
        String lowerInput = input.toLowerCase();
        boolean isCrypto = CRYPTO_TICKERS.values().stream()
                .anyMatch(info -> info.coinGeckoId().equals(lowerInput));

        return isCrypto ? AssetType.CRYPTO : AssetType.STOCK;
    }


    /**
     * Convert user input to CoinGecko ID
     * Supports both ticker (BTC) and CoinGecko ID (bitcoin)
     */
    public static String toCoinGeckoId(String input) {
        String upperInput = input.toUpperCase();

        // Check if it's a known ticker
        if (CRYPTO_TICKERS.containsKey(upperInput)) {
            return CRYPTO_TICKERS.get(upperInput).coinGeckoId();
        }

        // Check if it's already a CoinGecko ID
        String lowerInput = input.toLowerCase();
        Optional<CoinInfo> byId = CRYPTO_TICKERS.values().stream()
                .filter(info -> info.coinGeckoId().equals(lowerInput))
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
        if (CRYPTO_TICKERS.containsKey(upperInput)) {
            return CRYPTO_TICKERS.get(upperInput);
        }

        // Try CoinGecko ID
        String lowerInput = input.toLowerCase();
        Optional<CoinInfo> byId = CRYPTO_TICKERS.values().stream()
                .filter(info -> info.coinGeckoId().equals(lowerInput))
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
        if (CRYPTO_TICKERS.containsKey(upperInput)) {
            return true;
        }

        String lowerInput = input.toLowerCase();
        return CRYPTO_TICKERS.values().stream()
                .anyMatch(info -> info.coinGeckoId().equals(lowerInput));
    }

    /**
     * Get all supported tickers for help message
     */
    public static String getSupportedCryptoMessage() {
        StringBuilder sb = new StringBuilder("**Popular Tickers:**\n\n");

        String[] featured = {"BTC", "ETH", "SOL", "ADA", "DOGE", "XRP",
                "MATIC", "AVAX", "DOT", "LINK", "UNI", "ATOM"};

        for (String ticker : featured) {
            CoinInfo info = CRYPTO_TICKERS.get(ticker);
            if (info != null) {
                sb.append(String.format("• **%s** - %s\n", ticker, info.fullName()));
            }
        }

        sb.append("\n_...and ").append(CRYPTO_TICKERS.size() - featured.length)
                .append(" more!_\n\n");
        sb.append("💡 Use `/tickers` to see the full list");

        return sb.toString();
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    /**
     * Get supported stock tickers message
     */
    public static String getSupportedStocksMessage() {
        StringBuilder sb = new StringBuilder("**📈 Popular Stocks:**\n\n");

        String[] featured = {"AAPL", "MSFT", "GOOGL", "AMZN", "TSLA", "NVDA",
                "META", "JPM", "V", "WMT", "NFLX", "DIS"};

        for (String ticker : featured) {
            StockInfo info = STOCK_TICKERS.get(ticker);
            if (info != null) {
                sb.append(String.format("• **%s** - %s\n", ticker, info.fullName()));
            }
        }

        sb.append("\n_Plus thousands more US & international stocks!_");

        return sb.toString();
    }

    /**
     * Get all tickers organized by category
     */
    public static String getAllTickersMessage() {
        StringBuilder sb = new StringBuilder();

        sb.append("💼 **All Supported Assets**\n\n");

        // Crypto section
        sb.append(getSupportedCryptoMessage());
        sb.append("\n\n");

        // Stock section
        sb.append(getSupportedStocksMessage());

        sb.append("\n\n");
        sb.append(String.format("**Total:** %d cryptocurrencies + %d stocks",
                CRYPTO_TICKERS.size(), STOCK_TICKERS.size()));

        return sb.toString();
    }

    // ===== Record Classes =====
    public record CoinInfo(String ticker, String coinGeckoId, String fullName) {
        public String displayName() {
            return fullName + " (" + ticker + ")";
        }
    }

    public record StockInfo(String ticker, String fullName) {
        public String displayName() {
            return fullName + " (" + ticker + ")";
        }
    }

    public record AssetInfo(
            String ticker,
            String fullName,
            AssetType type,
            String displayName
    ) {}

    public enum AssetType {
        CRYPTO,
        STOCK,
        FOREX,
        ETF,
        INDEX
    }
}
