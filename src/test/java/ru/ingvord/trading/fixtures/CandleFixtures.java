package ru.ingvord.trading.fixtures;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Synthesised market data in Bybit's kline response format, plus the recorded real response.
 */
public final class CandleFixtures {
    private static final long START_MS = 1_795_000_000_000L;
    private static final long FOUR_HOURS_MS = 4L * 60 * 60 * 1000;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CandleFixtures() {}

    /** Steady uptrend with some down candles: RSI ends well above 70. */
    public static List<BigDecimal> rally(int n, long seed) {
        return walk(n, 0.004, 0.006, seed);
    }

    /** Steady downtrend with some up candles: RSI ends well below 30. */
    public static List<BigDecimal> selloff(int n, long seed) {
        return walk(n, -0.004, 0.006, seed);
    }

    /**
     * Range-bound market: a ~7-candle oscillation plus light noise. A pure random walk can wander
     * into an RSI zone, which would make "normal" scenarios seed-dependent.
     */
    public static List<BigDecimal> sideways(int n, long seed) {
        Random random = new Random(seed);
        List<BigDecimal> closes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double price = 100 * (1 + 0.01 * Math.sin(i * 2 * Math.PI / 7) + (random.nextDouble() * 2 - 1) * 0.001);
            closes.add(BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP));
        }
        return closes;
    }

    private static List<BigDecimal> walk(int n, double drift, double noise, long seed) {
        Random random = new Random(seed);
        List<BigDecimal> closes = new ArrayList<>(n);
        double price = 100;
        for (int i = 0; i < n; i++) {
            price *= 1 + drift + (random.nextDouble() * 2 - 1) * noise;
            closes.add(BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP));
        }
        return closes;
    }

    /** Bybit kline envelope (newest candle first) built from closes given oldest first. */
    public static String klineJson(String symbol, List<BigDecimal> closesOldestFirst) {
        StringBuilder rows = new StringBuilder();
        for (int i = closesOldestFirst.size() - 1; i >= 0; i--) {
            BigDecimal close = closesOldestFirst.get(i);
            BigDecimal open = i == 0 ? close : closesOldestFirst.get(i - 1);
            BigDecimal high = open.max(close).multiply(new BigDecimal("1.002")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal low = open.min(close).multiply(new BigDecimal("0.998")).setScale(2, RoundingMode.HALF_UP);
            if (rows.length() > 0) rows.append(',');
            rows.append(String.format(Locale.ROOT, "[\"%d\",\"%s\",\"%s\",\"%s\",\"%s\",\"10\",\"1000\"]",
                    START_MS + i * FOUR_HOURS_MS, open, high, low, close));
        }
        return String.format(Locale.ROOT,
                "{\"retCode\":0,\"retMsg\":\"OK\",\"result\":{\"category\":\"spot\",\"symbol\":\"%s\",\"list\":[%s]},\"retExtInfo\":{},\"time\":0}",
                symbol, rows);
    }

    /** Bybit's error envelope for an unknown symbol (HTTP 200, retCode != 0). */
    public static String invalidSymbolJson() {
        return "{\"retCode\":10001,\"retMsg\":\"Invalid symbol\",\"result\":{},\"retExtInfo\":{},\"time\":0}";
    }

    /** Real BTCUSDT 4h response captured from api.bybit.com. */
    public static String recordedKlines() {
        try (var in = CandleFixtures.class.getResourceAsStream("/fixtures/bybit/kline_btcusdt_240.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** One kline column (1=open, 2=high, 3=low, 4=close) of a Bybit response, oldest first. */
    public static List<BigDecimal> column(String klineJson, int column) {
        try {
            List<BigDecimal> values = new ArrayList<>();
            for (JsonNode row : MAPPER.readTree(klineJson).path("result").path("list")) {
                values.add(new BigDecimal(row.get(column).asText()));
            }
            Collections.reverse(values);
            return values;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
