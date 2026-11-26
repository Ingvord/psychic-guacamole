package ru.ingvord.trading.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TickerMapperTest {

    @Test
    void toBybitSymbol_shortNames() {
        assertEquals("BTCUSDT", TickerMapper.toBybitSymbol("btc"));
        assertEquals("BTCUSDT", TickerMapper.toBybitSymbol("BTC"));
        assertEquals("BTCUSDT", TickerMapper.toBybitSymbol("  btc "));
        assertEquals("SOLUSDT", TickerMapper.toBybitSymbol("sol"));
    }

    @Test
    void toBybitSymbol_stocksAndEtfs() {
        assertEquals("QQQUSDT", TickerMapper.toBybitSymbol("qqq"));
        assertEquals("QQQUSDT", TickerMapper.toBybitSymbol("QQQUSDT"));
        assertEquals("AAPLUSDT", TickerMapper.toBybitSymbol("aapl"));
        assertEquals("AAPLXUSDT", TickerMapper.toBybitSymbol("aaplx"));
    }

    @Test
    void toBybitSymbol_fullSymbolsAndSeparators() {
        assertEquals("ETHUSDT", TickerMapper.toBybitSymbol("ethusdt"));
        assertEquals("ETHUSDC", TickerMapper.toBybitSymbol("ETHUSDC"));
        assertEquals("BTCUSDT", TickerMapper.toBybitSymbol("BTC/USD"));
        assertEquals("BTCUSDT", TickerMapper.toBybitSymbol("BTC/USDT"));
        assertEquals("ETHUSDT", TickerMapper.toBybitSymbol("eth-usdt"));
        assertEquals("1000PEPEUSDT", TickerMapper.toBybitSymbol("1000pepe"));
    }

    @Test
    void toBybitSymbol_legacyNamesFromStoredAlerts() {
        assertEquals("BTCUSDT", TickerMapper.toBybitSymbol("bitcoin"));
        assertEquals("DOGEUSDT", TickerMapper.toBybitSymbol("dogecoin"));
        assertEquals("AVAXUSDT", TickerMapper.toBybitSymbol("avalanche-2"));
    }

    @Test
    void toBybitSymbol_tokensRenamedOnBybit() {
        assertEquals("POLUSDT", TickerMapper.toBybitSymbol("matic"));
        assertEquals("POLUSDT", TickerMapper.toBybitSymbol("MATIC"));
        assertEquals("POLUSDT", TickerMapper.toBybitSymbol("MATIC/USD"));
        assertEquals("SUSDT", TickerMapper.toBybitSymbol("ftm"));
        assertEquals("RENDERUSDT", TickerMapper.toBybitSymbol("rndr"));
    }
}
