package ru.ingvord.trading.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class RsiCalculatorTest {

    // StockCharts RSI worked example (14-period)
    private static final List<BigDecimal> CLOSES = Stream.of(
            "44.34", "44.09", "44.15", "43.61", "44.33", "44.83", "45.10", "45.42", "45.84", "46.08",
            "45.89", "46.03", "45.61", "46.28", "46.28", "46.00", "46.03", "46.41", "46.22", "45.64",
            "46.21", "46.25", "45.71", "46.45", "45.78", "45.35", "44.03", "44.18", "44.22", "44.57",
            "43.42", "42.66", "43.13").map(BigDecimal::new).toList();

    @Test
    void wilder_matchesPublishedReferenceValues() {
        // exact values from this implementation (full-precision averages)
        assertEquals(new BigDecimal("70.46"), RsiCalculator.wilder(CLOSES.subList(0, 15), 14));
        assertEquals(new BigDecimal("66.25"), RsiCalculator.wilder(CLOSES.subList(0, 16), 14));
        assertEquals(new BigDecimal("66.48"), RsiCalculator.wilder(CLOSES.subList(0, 17), 14));
        assertEquals(new BigDecimal("37.79"), RsiCalculator.wilder(CLOSES, 14));

        // StockCharts publishes 70.53 / 66.32 / 66.55 / 37.77: their sheet rounds the averages to 2 decimals
        assertEquals(70.53, RsiCalculator.wilder(CLOSES.subList(0, 15), 14).doubleValue(), 0.1);
        assertEquals(66.32, RsiCalculator.wilder(CLOSES.subList(0, 16), 14).doubleValue(), 0.1);
        assertEquals(66.55, RsiCalculator.wilder(CLOSES.subList(0, 17), 14).doubleValue(), 0.1);
        assertEquals(37.77, RsiCalculator.wilder(CLOSES, 14).doubleValue(), 0.1);
    }

    @Test
    void wilder_monotonicRise_is100() {
        var rising = Stream.iterate(BigDecimal.ONE, v -> v.add(BigDecimal.ONE)).limit(30).toList();
        assertEquals(new BigDecimal("100.00"), RsiCalculator.wilder(rising, 14));
    }

    @Test
    void wilder_monotonicFall_is0() {
        var falling = Stream.iterate(new BigDecimal("100"), v -> v.subtract(BigDecimal.ONE)).limit(30).toList();
        assertEquals(new BigDecimal("0.00"), RsiCalculator.wilder(falling, 14));
    }

    @Test
    void wilder_flat_is50() {
        var flat = Collections.nCopies(30, new BigDecimal("42"));
        assertEquals(new BigDecimal("50.00"), RsiCalculator.wilder(flat, 14));
    }

    @Test
    void wilder_tooFewValues_throws() {
        assertThrows(IllegalArgumentException.class, () -> RsiCalculator.wilder(CLOSES.subList(0, 14), 14));
    }
}
