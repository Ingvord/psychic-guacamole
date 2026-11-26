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
import java.util.List;
import java.util.Map;

/**
 * Bybit public market data (spot, no API key). Computes RSI from kline candles.
 *
 * API: https://bybit-exchange.github.io/docs/v5/market/kline
 */
public class Bybit implements IndicatorService {
    private static final String BASE_URL = "https://api.bybit.com";

    private static final Map<String, String> INTERVALS = Map.of(
            "15m", "15", "30m", "30", "1h", "60", "2h", "120", "4h", "240", "1d", "D", "1w", "W");
    // kline row: [startTime, open, high, low, close, volume, turnover]
    private static final Map<String, Integer> SERIES_COLUMNS = Map.of(
            "open", 1, "high", 2, "low", 3, "close", 4);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Bybit(HttpClient httpClient) {
        this.httpClient = httpClient;
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

        int limit = Math.min(1000, Math.max(200, period * 10));
        String url = String.format("%s/v5/market/kline?category=spot&symbol=%s&interval=%s&limit=%d",
                BASE_URL, TickerMapper.toBybitSymbol(symbol), bybitInterval, limit);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Bybit kline request failed with status: " +
                    response.statusCode() + ", body: " + response.body());
        }

        JsonNode json = objectMapper.readTree(response.body());
        if (json.path("retCode").asInt(-1) != 0) {
            throw new IOException("Bybit API error " + json.path("retCode").asText() + ": " + json.path("retMsg").asText());
        }

        // result.list is newest first
        List<BigDecimal> values = new ArrayList<>();
        for (JsonNode row : json.path("result").path("list")) {
            values.add(new BigDecimal(row.get(column).asText()));
        }
        Collections.reverse(values);

        if (values.size() < period + 1) {
            throw new IOException("Not enough candles for " + symbol + " " + interval + ": got " + values.size());
        }

        return RsiCalculator.wilder(values, period);
    }
}
