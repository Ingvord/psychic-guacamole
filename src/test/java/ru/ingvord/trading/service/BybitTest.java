package ru.ingvord.trading.service;

import org.junit.jupiter.api.Test;
import ru.ingvord.trading.fixtures.CandleFixtures;
import ru.ingvord.trading.fixtures.FakeHttp;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static ru.ingvord.trading.fixtures.FakeHttp.query;

class BybitTest {

    private static final FakeHttp.Stub INVALID = new FakeHttp.Stub(200, CandleFixtures.invalidSymbolJson());

    // ---------------------------------------------------------------- RSI

    @Test
    void getRsi_parsesRecordedResponse() throws Exception {
        var http = FakeHttp.offline();
        var closes = CandleFixtures.column(CandleFixtures.recordedKlines(), 4);

        BigDecimal rsi = new Bybit(http.client).getRsi("btc", "4h", 14, "close");

        assertEquals(RsiCalculator.wilder(closes, 14), rsi);
        assertTrue(rsi.signum() > 0 && rsi.compareTo(new BigDecimal("100")) < 0, "RSI out of range: " + rsi);
    }

    @Test
    void getRsi_buildsRequest_linearFirst() throws Exception {
        var http = FakeHttp.offline();

        new Bybit(http.client).getRsi("bitcoin", "4h", 14, "close");

        assertEquals(1, http.bybitCalls.size());
        String uri = http.bybitCalls.get(0).toString();
        assertTrue(uri.startsWith("https://api.bybit.com/v5/market/kline?"), uri);
        assertTrue(uri.contains("category=linear&symbol=BTCUSDT&interval=240&limit=200"), uri);
    }

    @Test
    void getRsi_mapsTimeframes() throws Exception {
        var http = FakeHttp.offline();
        var bybit = new Bybit(http.client);

        for (String tf : new String[]{"15m", "30m", "1h", "2h", "4h", "1d", "1w"}) {
            bybit.getRsi("eth", tf, 14, "close");
        }

        var intervals = http.bybitCalls.stream().map(u -> query(u, "interval")).toList();
        assertEquals(List.of("15", "30", "60", "120", "240", "D", "W"), intervals);
    }

    @Test
    void getRsi_stockEtf_usesLinear() throws Exception {
        var http = FakeHttp.offline();

        BigDecimal rsi = new Bybit(http.client).getRsi("qqq", "1h", 14, "close");

        assertEquals(1, http.bybitCalls.size());
        assertTrue(http.bybitCalls.get(0).toString().contains("category=linear&symbol=QQQUSDT&interval=60"));
        assertEquals(RsiCalculator.wilder(CandleFixtures.column(CandleFixtures.recordedQqqKlines(), 4), 14), rsi);
    }

    @Test
    void getRsi_usesNewestLastOrdering() throws Exception {
        // Bybit lists newest first: a rally must read as RSI 100, not 0
        var rising = java.util.stream.Stream.iterate(BigDecimal.TEN, v -> v.add(BigDecimal.ONE)).limit(50).toList();
        var http = FakeHttp.offline().bybitKlines(CandleFixtures.klineJson("BTCUSDT", rising));

        assertEquals(new BigDecimal("100.00"), new Bybit(http.client).getRsi("btc", "1h", 14, "close"));
    }

    @Test
    void getRsi_selectsSeriesColumn() throws Exception {
        var http = FakeHttp.offline();
        var recorded = CandleFixtures.recordedKlines();

        BigDecimal high = new Bybit(http.client).getRsi("btc", "4h", 14, "high");

        assertEquals(RsiCalculator.wilder(CandleFixtures.column(recorded, 2), 14), high);
        assertNotEquals(new Bybit(http.client).getRsi("btc", "4h", 14, "close"), high);
    }

    @Test
    void getRsi_fallsBackToSpot_andRemembersTheCategory() throws Exception {
        var http = FakeHttp.offline().bybit(uri -> "linear".equals(query(uri, "category"))
                ? INVALID
                : new FakeHttp.Stub(200, CandleFixtures.recordedKlines()));
        var bybit = new Bybit(http.client);

        bybit.getRsi("ftt", "4h", 14, "close");
        assertEquals(List.of("linear", "spot"), http.bybitCalls.stream().map(u -> query(u, "category")).toList());

        http.reset();
        bybit.getRsi("ftt", "1h", 14, "close");
        assertEquals(List.of("spot"), http.bybitCalls.stream().map(u -> query(u, "category")).toList(),
                "second lookup goes straight to the remembered category");
    }

    @Test
    void getRsi_unknownEverywhere_throwsAfterTryingBothCategories() {
        var http = FakeHttp.offline().bybit(uri -> INVALID);

        var e = assertThrows(IOException.class, () -> new Bybit(http.client).getRsi("zzzz", "1d", 14, "close"));

        assertTrue(e.getMessage().contains("not found"), e.getMessage());
        assertEquals(2, http.bybitCalls.size());
    }

    @Test
    void getRsi_http500_throwsWithoutFallback() {
        var http = FakeHttp.offline().bybit(uri -> new FakeHttp.Stub(500, "oops"));

        assertThrows(IOException.class, () -> new Bybit(http.client).getRsi("btc", "4h", 14, "close"));
        assertEquals(1, http.bybitCalls.size(), "a transport error says nothing about the symbol");
    }

    @Test
    void getRsi_tooFewCandles_throwsIOException() {
        var few = CandleFixtures.sideways(10, 1);
        var http = FakeHttp.offline().bybitKlines(CandleFixtures.klineJson("BTCUSDT", few));

        assertThrows(IOException.class, () -> new Bybit(http.client).getRsi("btc", "4h", 14, "close"));
    }

    // ---------------------------------------------------------------- prices

    @Test
    void getCurrentPrice_stockEtf_linear() throws Exception {
        var http = FakeHttp.offline();

        BigDecimal price = new Bybit(http.client).getCurrentPrice("qqq");

        assertEquals(new BigDecimal("638.40"), price);
        assertEquals(1, http.bybitCalls.size());
        assertTrue(http.bybitCalls.get(0).toString().endsWith("/v5/market/tickers?category=linear&symbol=QQQUSDT"));
    }

    @Test
    void getCurrentPrice_spotOnly_fallsBackAfterEmptyLinearList() throws Exception {
        var http = FakeHttp.offline();

        // FTTUSDT is a known symbol that linear answers with retCode 0 and an empty list
        assertEquals(new BigDecimal("0.182"), new Bybit(http.client).getCurrentPrice("ftt"));
        assertEquals(List.of("linear", "spot"), http.bybitCalls.stream().map(u -> query(u, "category")).toList());
    }

    @Test
    void getCurrentPrice_unknown_throwsIOException() {
        var http = FakeHttp.offline();

        assertThrows(IOException.class, () -> new Bybit(http.client).getCurrentPrice("zzzz"));
        assertEquals(2, http.bybitCalls.size());
    }

    @Test
    void getPrices_oneLinearSnapshot_keyedByInputSymbol() throws Exception {
        var http = FakeHttp.offline();

        Map<String, BigDecimal> prices = new Bybit(http.client).getPrices(Set.of("BTC", "QQQ", "BITCOIN", "MATIC"));

        assertEquals(1, http.bybitCalls.size(), "every symbol is on linear: one snapshot, no spot call");
        assertEquals("https://api.bybit.com/v5/market/tickers?category=linear", http.bybitCalls.get(0).toString());
        assertEquals(Map.of(
                "BTC", new BigDecimal("60614.60"),
                "BITCOIN", new BigDecimal("60614.60"),
                "QQQ", new BigDecimal("638.40"),
                "MATIC", new BigDecimal("0.07656")), prices);
    }

    @Test
    void getPrices_spotOnlySymbol_triggersOneSpotSnapshot() throws Exception {
        var http = FakeHttp.offline();

        Map<String, BigDecimal> prices = new Bybit(http.client).getPrices(Set.of("QQQ", "FTT"));

        assertEquals(List.of("linear", "spot"), http.bybitCalls.stream().map(u -> query(u, "category")).toList());
        assertEquals(new BigDecimal("638.40"), prices.get("QQQ"));
        assertEquals(new BigDecimal("0.182"), prices.get("FTT"));
    }

    @Test
    void getPrices_unknownSymbolIsAbsent() throws Exception {
        var http = FakeHttp.offline();

        Map<String, BigDecimal> prices = new Bybit(http.client).getPrices(Set.of("BTC", "ZZZZ"));

        assertEquals(Set.of("BTC"), prices.keySet());
    }

    @Test
    void getPrices_empty_makesNoRequest() throws Exception {
        var http = FakeHttp.offline();

        assertEquals(Map.of(), new Bybit(http.client).getPrices(Set.of()));
        assertEquals(List.<URI>of(), http.bybitCalls);
    }

    @Test
    void getPrices_http500_throwsIOException() {
        var http = FakeHttp.offline().bybit(uri -> new FakeHttp.Stub(500, "down"));

        assertThrows(IOException.class, () -> new Bybit(http.client).getPrices(Set.of("BTC")));
    }
}
