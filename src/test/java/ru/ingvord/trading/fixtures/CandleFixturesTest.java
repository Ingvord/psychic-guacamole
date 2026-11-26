package ru.ingvord.trading.fixtures;

import org.junit.jupiter.api.Test;
import ru.ingvord.trading.model.RsiAlert;
import ru.ingvord.trading.service.RsiCalculator;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Guards the synthesised series that the scenario tests rely on for their zones. */
class CandleFixturesTest {

    @Test
    void generatedSeries_landInTheirIntendedZones() {
        for (long seed = 1; seed <= 5; seed++) {
            assertEquals(RsiAlert.ZONE_OVERBOUGHT, zone(CandleFixtures.rally(200, seed)), "rally seed " + seed);
            assertEquals(RsiAlert.ZONE_OVERSOLD, zone(CandleFixtures.selloff(200, seed)), "selloff seed " + seed);
            assertEquals(RsiAlert.ZONE_NORMAL, zone(CandleFixtures.sideways(200, seed)), "sideways seed " + seed);
        }
    }

    @Test
    void recordedFixture_isRealBybitFormat() {
        var closes = CandleFixtures.column(CandleFixtures.recordedKlines(), 4);
        assertEquals(200, closes.size());
    }

    private static String zone(java.util.List<java.math.BigDecimal> closes) {
        return RsiAlert.zoneOf(RsiCalculator.wilder(closes, 14));
    }
}
