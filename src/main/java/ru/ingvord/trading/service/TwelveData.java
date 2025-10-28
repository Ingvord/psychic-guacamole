package ru.ingvord.trading.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Twelve Data Service - Unified price provider for stocks, crypto, forex, and ETFs
 *
 * API Documentation: https://twelvedata.com/docs
 *
 * Features:
 * - Unified API for all asset classes (stocks, crypto, forex, ETFs)
 * - Batch requests support (up to 120 symbols per request)
 * - Real-time and historical data
 * - Global coverage: US + 90+ international exchanges, 180+ crypto exchanges
 *
 * Free Tier Limits:
 * - 8 API calls per minute
 * - 800 API calls per day
 * - US equities, forex, and crypto included
 *
 * Symbol Format:
 * - Stocks: AAPL, TSLA, MSFT (ticker as-is)
 * - Crypto: BTC/USD, ETH/USD (base/quote format)
 * - Forex: EUR/USD, GBP/JPY
 */
public class TwelveData implements PriceService {
    private static final String BASE_URL = "https://api.twelvedata.com";
    private static final String PRICE_ENDPOINT = "/price";
    private static final int PRICE_SCALE = 4;
    private static final int MAX_BATCH_SIZE = 120; // Twelve Data limit

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public TwelveData(HttpClient httpClient, String apiKey) {
        this.httpClient = httpClient;
        this.apiKey = apiKey;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Get current price for a single symbol
     *
     * @param symbol Stock ticker (AAPL) or crypto pair (BTC/USD)
     * @return Current price in USD
     * @throws IOException If API request fails
     */
    @Override
    public BigDecimal getCurrentPrice(String symbol) throws IOException, InterruptedException {
        // Convert symbol to Twelve Data format if needed
        String twelveDataSymbol = TickerMapper.toTwelveDataSymbol(symbol);

        String url = String.format("%s%s?symbol=%s&apikey=%s",
                BASE_URL, PRICE_ENDPOINT, twelveDataSymbol, apiKey);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Twelve Data API request failed with status: " +
                    response.statusCode() + ", body: " + response.body());
        }

        JsonNode jsonNode = objectMapper.readTree(response.body());

        // Check for API errors
        if (jsonNode.has("status") && "error".equals(jsonNode.get("status").asText())) {
            String message = jsonNode.has("message") ? jsonNode.get("message").asText() : "Unknown error";
            throw new IOException("Twelve Data API error: " + message);
        }

        if (!jsonNode.has("price")) {
            throw new IOException("Price not found for symbol: " + symbol +
                    ". Response: " + response.body());
        }

        String priceStr = jsonNode.get("price").asText();
        return new BigDecimal(priceStr).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Get current prices for multiple symbols in a single batch request
     * This is much more efficient than multiple individual requests
     *
     * @param symbols Set of stock tickers and/or crypto pairs
     * @return Map of symbol -> price
     * @throws IOException If API request fails
     */
    @Override
    public Map<String, BigDecimal> getPrices(Set<String> symbols) throws IOException, InterruptedException {
        if (symbols == null || symbols.isEmpty()) {
            return Map.of();
        }

        // Convert all symbols to Twelve Data format
        Set<String> twelveDataSymbols = symbols.stream()
                .map(TickerMapper::toTwelveDataSymbol)
                .collect(Collectors.toSet());

        // If more than MAX_BATCH_SIZE, split into multiple requests
        if (twelveDataSymbols.size() > MAX_BATCH_SIZE) {
            return getPricesInBatches(symbols);
        }

        // Build comma-separated symbol list
        String symbolList = String.join(",", twelveDataSymbols);

        String url = String.format("%s%s?symbol=%s&apikey=%s",
                BASE_URL, PRICE_ENDPOINT, symbolList, apiKey);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Twelve Data batch API request failed with status: " +
                    response.statusCode());
        }

        return parseBatchResponse(response.body());
    }

    /**
     * Handle requests larger than MAX_BATCH_SIZE by splitting into multiple batches
     */
    private Map<String, BigDecimal> getPricesInBatches(Set<String> symbols)
            throws IOException, InterruptedException {
        Map<String, BigDecimal> allPrices = new HashMap<>();

        var symbolList = symbols.stream().toList();
        for (int i = 0; i < symbolList.size(); i += MAX_BATCH_SIZE) {
            int end = Math.min(i + MAX_BATCH_SIZE, symbolList.size());
            Set<String> batch = new HashSet<>(symbolList.subList(i, end));

            Map<String, BigDecimal> batchPrices = getPrices(batch);
            allPrices.putAll(batchPrices);

            // Rate limiting: wait a bit between batches
            if (end < symbolList.size()) {
                Thread.sleep(8000); // 8 seconds to stay under 8 calls/min
            }
        }

        return allPrices;
    }

    /**
     * Parse batch response which can be either:
     * 1. Object with symbol keys (multiple symbols)
     * 2. Single object with price field (one symbol)
     */
    private Map<String, BigDecimal> parseBatchResponse(String responseBody)
            throws IOException {
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        Map<String, BigDecimal> prices = new HashMap<>();

        // Check if response is an array or object
        if (jsonNode.isObject()) {
            // Could be single symbol response or batch response
            // Batch response - iterate over each symbol
            jsonNode.fields().forEachRemaining(entry -> {
                String twelveDataSymbol = entry.getKey();
                JsonNode data = entry.getValue();

                try {
                    if (data.has("price")) {
                        String priceStr = data.get("price").asText();
                        BigDecimal price = new BigDecimal(priceStr)
                                .setScale(PRICE_SCALE, RoundingMode.HALF_UP);

                        // Map back to original symbol format
                        String originalSymbol = TickerMapper.fromTwelveDataSymbol(twelveDataSymbol);
                        prices.put(originalSymbol, price);
                    }
                } catch (Exception e) {
                    // Skip symbols with errors, log them
                    System.err.println("Error parsing price for " + twelveDataSymbol + ": " + e.getMessage());
                }
            });
        }

        return prices;
    }

    /**
     * Check if a symbol is supported by attempting to fetch its price
     *
     * @param symbol Symbol to check
     * @return true if symbol is supported and has valid price data
     */
    public boolean isSymbolSupported(String symbol) {
        try {
            getCurrentPrice(symbol);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}