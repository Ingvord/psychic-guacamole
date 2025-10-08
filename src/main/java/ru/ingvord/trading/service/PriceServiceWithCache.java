package ru.ingvord.trading.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PriceServiceWithCache implements PriceService {
    private final PriceService delegate;
    private final Map<String, CachedPrice> cache = new ConcurrentHashMap<>();
    private final long cacheTtlSeconds;

    public PriceServiceWithCache(PriceService delegate, long cacheTtlSeconds) {
        this.delegate = delegate;
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    public PriceServiceWithCache(PriceService delegate) {
        this.delegate = delegate;
        this.cacheTtlSeconds = 60L;
    }

    @Override
    public BigDecimal getCurrentPrice(String symbol) throws IOException, InterruptedException {
        CachedPrice cached = cache.get(symbol);

        // Return cached price if still valid
        if (cached != null && !cached.isExpired(cacheTtlSeconds)) {
            return cached.price;
        }

        // Fetch fresh price
        BigDecimal price = delegate.getCurrentPrice(symbol);
        cache.putIfAbsent(symbol, new CachedPrice(price, Instant.now()));

        return price;
    }


    private record CachedPrice(BigDecimal price, Instant timestamp) {
        boolean isExpired(long ttlSeconds) {
            return Instant.now().isAfter(timestamp.plusSeconds(ttlSeconds));
        }
    }
}
