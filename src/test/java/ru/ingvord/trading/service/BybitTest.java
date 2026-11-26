package ru.ingvord.trading.service;

import org.junit.jupiter.api.Test;
import ru.ingvord.trading.fixtures.CandleFixtures;
import ru.ingvord.trading.fixtures.FakeHttp;

import java.io.IOException;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class BybitTest {

    @Test
    void getRsi_parsesRecordedResponse() throws Exception {
        var http = FakeHttp.offline();
        var closes = CandleFixtures.column(CandleFixtures.recordedKlines(), 4);

        BigDecimal rsi = new Bybit(http.client).getRsi("btc", "4h", 14, "close");

        assertEquals(RsiCalculator.wilder(closes, 14), rsi);
        assertTrue(rsi.signum() > 0 && rsi.compareTo(new BigDecimal("100")) < 0, "RSI out of range: " + rsi);
    }

    @Test
    void getRsi_buildsRequest() throws Exception {
        var http = FakeHttp.offline();

        new Bybit(http.client).getRsi("bitcoin", "4h", 14, "close");

        assertEquals(1, http.bybitCalls.size());
        String uri = http.bybitCalls.get(0).toString();
        assertTrue(uri.startsWith("https://api.bybit.com/v5/market/kline?"), uri);
        assertTrue(uri.contains("category=spot&symbol=BTCUSDT&interval=240&limit=200"), uri);
    }

    @Test
    void getRsi_mapsTimeframes() throws Exception {
        var http = FakeHttp.offline();
        var bybit = new Bybit(http.client);

        for (String tf : new String[]{"15m", "30m", "1h", "2h", "4h", "1d", "1w"}) {
            bybit.getRsi("eth", tf, 14, "close");
        }

        var intervals = http.bybitCalls.stream().map(u -> u.getQuery().replaceAll(".*interval=([^&]+).*", "$1")).toList();
        assertEquals(java.util.List.of("15", "30", "60", "120", "240", "D", "W"), intervals);
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
    void getRsi_bybitErrorEnvelope_throwsIOException() {
        var http = FakeHttp.offline().bybit(uri -> new FakeHttp.Stub(200, CandleFixtures.invalidSymbolJson()));

        var e = assertThrows(IOException.class, () -> new Bybit(http.client).getRsi("aapl", "1d", 14, "close"));
        assertTrue(e.getMessage().contains("10001"), e.getMessage());
    }

    @Test
    void getRsi_http500_throwsIOException() {
        var http = FakeHttp.offline().bybit(uri -> new FakeHttp.Stub(500, "oops"));

        assertThrows(IOException.class, () -> new Bybit(http.client).getRsi("btc", "4h", 14, "close"));
    }

    @Test
    void getRsi_tooFewCandles_throwsIOException() {
        var few = CandleFixtures.sideways(10, 1);
        var http = FakeHttp.offline().bybitKlines(CandleFixtures.klineJson("BTCUSDT", few));

        assertThrows(IOException.class, () -> new Bybit(http.client).getRsi("btc", "4h", 14, "close"));
    }
}
