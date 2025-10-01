package ru.ingvord.trading.service;

import java.io.IOException;
import java.math.BigDecimal;

public interface PriceService {
    BigDecimal getCurrentPrice(String symbol) throws IOException, InterruptedException;
}
