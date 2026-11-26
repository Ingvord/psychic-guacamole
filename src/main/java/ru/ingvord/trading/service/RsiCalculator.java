package ru.ingvord.trading.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Wilder's RSI, the variant used by TradingView and exchange charts.
 */
public final class RsiCalculator {
    private RsiCalculator() {}

    /**
     * @param oldestFirst price series, oldest value first
     * @param period      RSI period (needs at least period + 1 values)
     * @return RSI of the last value, scaled to 2 decimal places
     */
    public static BigDecimal wilder(List<BigDecimal> oldestFirst, int period) {
        if (period < 1 || oldestFirst.size() < period + 1) {
            throw new IllegalArgumentException("Need at least " + (period + 1) + " values for RSI(" + period + "), got " + oldestFirst.size());
        }

        double avgGain = 0;
        double avgLoss = 0;
        for (int i = 1; i <= period; i++) {
            double change = oldestFirst.get(i).subtract(oldestFirst.get(i - 1)).doubleValue();
            if (change > 0) avgGain += change; else avgLoss -= change;
        }
        avgGain /= period;
        avgLoss /= period;

        for (int i = period + 1; i < oldestFirst.size(); i++) {
            double change = oldestFirst.get(i).subtract(oldestFirst.get(i - 1)).doubleValue();
            avgGain = (avgGain * (period - 1) + Math.max(change, 0)) / period;
            avgLoss = (avgLoss * (period - 1) + Math.max(-change, 0)) / period;
        }

        if (avgLoss == 0) {
            return avgGain == 0 ? new BigDecimal("50.00") : new BigDecimal("100.00");
        }
        double rsi = 100 - 100 / (1 + avgGain / avgLoss);
        return BigDecimal.valueOf(rsi).setScale(2, RoundingMode.HALF_UP);
    }
}
