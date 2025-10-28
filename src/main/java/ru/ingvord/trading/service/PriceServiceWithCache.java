package ru.ingvord.trading.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
        cache.put(symbol, new CachedPrice(price, Instant.now()));

        return price;
    }

    @Override
    public Map<String, BigDecimal> getPrices(Set<String> symbols) throws IOException, InterruptedException {
        Map<String, BigDecimal> result = new HashMap<>();
        Set<String> symbolsToFetch = new HashSet<>();

        // Check cache first
        for (String symbol : symbols) {
            CachedPrice cached = cache.get(symbol);
            if (cached != null && !cached.isExpired(cacheTtlSeconds)) {
                result.put(symbol, cached.price);
            } else {
                symbolsToFetch.add(symbol);
            }
        }

        // Fetch missing prices in batch
        if (!symbolsToFetch.isEmpty()) {
            Map<String, BigDecimal> freshPrices = delegate.getPrices(symbolsToFetch);

            // Update cache and result
            Instant now = Instant.now();
            freshPrices.forEach((symbol, price) -> {
                cache.put(symbol, new CachedPrice(price, now));
                result.put(symbol, price);
            });
        }

        return result;
    }

    private record CachedPrice(BigDecimal price, Instant timestamp) {
        boolean isExpired(long ttlSeconds) {
            return Instant.now().isAfter(timestamp.plusSeconds(ttlSeconds));
        }
    }
}
