package ru.ingvord.trading.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bybit public market data (no API key): prices and RSI for crypto and stock/ETF contracts.
 *
 * Stocks and ETFs (QQQUSDT, AAPLUSDT...) are only listed as USDT perpetuals ("linear"); most crypto is
 * on both. Symbols are therefore resolved against {@link #CATEGORIES} in order.
 *
 * API: https://bybit-exchange.github.io/docs/v5/market/kline and .../market/tickers
 */
public class Bybit implements PriceService, IndicatorService {
    private static final String BASE_URL = "https://api.bybit.com";

    /** Resolution order: perpetuals first (covers stocks/ETFs), then spot. */
    static final List<String> CATEGORIES = List.of("linear", "spot");

    private static final Map<String, String> INTERVALS = Map.of(
            "15m", "15", "30m", "30", "1h", "60", "2h", "120", "4h", "240", "1d", "D", "1w", "W");
    // kline row: [startTime, open, high, low, close, volume, turnover]
    private static final Map<String, Integer> SERIES_COLUMNS = Map.of(
            "open", 1, "high", 2, "low", 3, "close", 4);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    /** Bybit symbol → category it was last found in, so warm Lambdas skip the miss. */
    private final Map<String, String> categoryBySymbol = new ConcurrentHashMap<>();

    public Bybit(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /** Last traded price. */
    @Override
    public BigDecimal getCurrentPrice(String symbol) throws IOException, InterruptedException {
        String bybitSymbol = TickerMapper.toBybitSymbol(symbol);

        for (String category : categoriesFor(bybitSymbol)) {
            try {
                JsonNode list = get(String.format("/v5/market/tickers?category=%s&symbol=%s", category, bybitSymbol), 5)
                        .path("list");
                BigDecimal price = list.isEmpty() ? null : parsePrice(list.get(0));
                if (price != null) {
                    categoryBySymbol.put(bybitSymbol, category);
                    return price;
                }
            } catch (NotListedException ignored) {
                // not in this category, try the next
            }
        }
        throw new IOException("Symbol not found on Bybit: " + symbol + " (" + bybitSymbol + ")");
    }

    /**
     * Prices for many symbols with at most one request per category: a full tickers snapshot of linear,
     * plus spot only when some symbol is not on linear.
     *
     * @return last prices keyed by the symbol exactly as passed in; unknown symbols are absent
     */
    @Override
    public Map<String, BigDecimal> getPrices(Set<String> symbols) throws IOException, InterruptedException {
        if (symbols == null || symbols.isEmpty()) {
            return Map.of();
        }

        Map<String, String> bybitByInput = new LinkedHashMap<>();
        for (String symbol : symbols) {
            bybitByInput.put(symbol, TickerMapper.toBybitSymbol(symbol));
        }

        Set<String> pending = new HashSet<>(bybitByInput.values());
        Map<String, BigDecimal> found = new HashMap<>();

        for (String category : CATEGORIES) {
            if (pending.isEmpty()) {
                break;
            }
            for (JsonNode ticker : get("/v5/market/tickers?category=" + category, 8).path("list")) {
                String bybitSymbol = ticker.path("symbol").asText();
                if (pending.contains(bybitSymbol)) {
                    BigDecimal price = parsePrice(ticker);
                    if (price != null) {
                        pending.remove(bybitSymbol);
                        found.put(bybitSymbol, price);
                        categoryBySymbol.put(bybitSymbol, category);
                    }
                }
            }
        }

        Map<String, BigDecimal> prices = new HashMap<>();
        bybitByInput.forEach((input, bybitSymbol) -> {
            BigDecimal price = found.get(bybitSymbol);
            if (price != null) {
                prices.put(input, price);
            }
        });
        return prices;
    }

    /**
     * Latest RSI (including the still-forming candle, like exchange charts).
     *
     * @param interval   user timeframe: 15m, 30m, 1h, 2h, 4h, 1d, 1w
     * @param seriesType close | open | high | low
     */
    @Override
    public BigDecimal getRsi(String symbol, String interval, int period, String seriesType)
            throws IOException, InterruptedException {
        String bybitInterval = INTERVALS.get(interval);
        Integer column = SERIES_COLUMNS.get(seriesType);
        if (bybitInterval == null || column == null) {
            throw new IOException("Unsupported interval/series: " + interval + "/" + seriesType);
        }

        String bybitSymbol = TickerMapper.toBybitSymbol(symbol);
        int limit = Math.min(1000, Math.max(200, period * 10));

        for (String category : categoriesFor(bybitSymbol)) {
            JsonNode candles;
            try {
                candles = get(String.format("/v5/market/kline?category=%s&symbol=%s&interval=%s&limit=%d",
                        category, bybitSymbol, bybitInterval, limit), 5).path("list");
            } catch (NotListedException e) {
                continue;
            }

            // result.list is newest first
            List<BigDecimal> values = new ArrayList<>();
            for (JsonNode row : candles) {
                values.add(new BigDecimal(row.get(column).asText()));
            }
            Collections.reverse(values);

            if (values.size() < period + 1) {
                throw new IOException("Not enough candles for " + symbol + " " + interval + ": got " + values.size());
            }
            categoryBySymbol.put(bybitSymbol, category);
            return RsiCalculator.wilder(values, period);
        }
        throw new IOException("Symbol not found on Bybit: " + symbol + " (" + bybitSymbol + ")");
    }

    /** Remembered category first, then the rest in resolution order. */
    private List<String> categoriesFor(String bybitSymbol) {
        String known = categoryBySymbol.get(bybitSymbol);
        if (known == null) {
            return CATEGORIES;
        }
        List<String> order = new ArrayList<>(CATEGORIES);
        order.remove(known);
        order.add(0, known);
        return order;
    }

    /** @return the response's {@code result} node */
    private JsonNode get(String pathAndQuery, int timeoutSeconds) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + pathAndQuery))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Bybit request failed with status: " +
                    response.statusCode() + ", body: " + response.body());
        }

        JsonNode json = objectMapper.readTree(response.body());
        if (json.path("retCode").asInt(-1) != 0) {
            throw new NotListedException("Bybit API error " + json.path("retCode").asText() + ": " + json.path("retMsg").asText());
        }
        return json.path("result");
    }

    private static BigDecimal parsePrice(JsonNode ticker) {
        String lastPrice = ticker.path("lastPrice").asText();
        return lastPrice.isEmpty() ? null : new BigDecimal(lastPrice);
    }

    /** Bybit answered 200 but with an error envelope, e.g. 10001 "Not supported symbols". */
    private static class NotListedException extends IOException {
        NotListedException(String message) {
            super(message);
        }
    }
}
