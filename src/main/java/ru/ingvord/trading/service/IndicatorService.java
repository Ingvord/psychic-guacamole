package ru.ingvord.trading.service;

import java.io.IOException;
import java.math.BigDecimal;

public interface IndicatorService {
    BigDecimal getRsi(String symbol, String interval, int period, String seriesType) throws IOException, InterruptedException;
}
