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

/**
 * Price Service - Fetches cryptocurrency prices from CoinGecko
 *
 * CoinGecko API:
 * - Free, no API key required
 * - 50+ requests per minute
 * - 13,000+ cryptocurrencies
 * - https://www.coingecko.com/en/api
 */
public class Coingecko implements PriceService {
    private static final String COINGECKO_API = "https://api.coingecko.com/api/v3/simple/price";
    private static final int PRICE_SCALE = 4;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public Coingecko(HttpClient httpClient) {
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Get current price for a cryptocurrency in USD
     *
     * @param symbol Cryptocurrency ID (e.g., "bitcoin", "ethereum", "cardano")
     * @return Current price in USD
     * @throws IOException If price fetch fails
     */
    public BigDecimal getCurrentPrice(String symbol) throws IOException, InterruptedException {
        // Convert to CoinGecko ID if it's a ticker
        String coingeckoId = TickerMapper.toCoinGeckoId(symbol);
        String url = COINGECKO_API + "?ids=" + coingeckoId + "&vs_currencies=usd";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("CoinGecko API request failed with status: " + response.statusCode());
        }

        JsonNode jsonNode = objectMapper.readTree(response.body());
        JsonNode priceNode = jsonNode.get(symbol.toLowerCase());

        if (priceNode == null || !priceNode.has("usd")) {
            throw new IOException("Price not found for symbol: " + symbol +
                    ". Make sure you're using the CoinGecko ID (e.g., 'bitcoin' not 'BTC')");
        }

        return new BigDecimal(priceNode.get("usd").asText()).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Check if a symbol is supported by attempting to fetch its price
     *
     * @param symbol Cryptocurrency ID to check
     * @return true if symbol is supported, false otherwise
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
