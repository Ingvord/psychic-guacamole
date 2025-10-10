package ru.ingvord.trading.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

public interface PriceService {
    BigDecimal getCurrentPrice(String symbol) throws IOException, InterruptedException;

    Map<String, BigDecimal> getPrices(Set<String> symbol) throws IOException, InterruptedException;
}
