package ru.ingvord.trading.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TickerMapperTest {

    @Test
    void toBybitSymbol() {
        assertEquals("BTCUSDT", TickerMapper.toBybitSymbol("btc"));
        assertEquals("BTCUSDT", TickerMapper.toBybitSymbol("BTC"));
        assertEquals("BTCUSDT", TickerMapper.toBybitSymbol("bitcoin"));
        assertEquals("BTCUSDT", TickerMapper.toBybitSymbol("BTC/USD"));
        assertEquals("BTCUSDT", TickerMapper.toBybitSymbol("BTC/USDT"));
        assertEquals("ETHUSDC", TickerMapper.toBybitSymbol("ethusdc"));
        assertEquals("SOLUSDT", TickerMapper.toBybitSymbol("sol"));
        assertEquals("DOGEUSDT", TickerMapper.toBybitSymbol("dogecoin"));
        // non-crypto is passed through and rejected by Bybit itself
        assertEquals("AAPLUSDT", TickerMapper.toBybitSymbol("aapl"));
    }
}
