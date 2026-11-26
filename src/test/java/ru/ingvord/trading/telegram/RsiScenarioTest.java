package ru.ingvord.trading.telegram;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.ingvord.trading.fixtures.*;
import ru.ingvord.trading.model.PriceAlert;
import ru.ingvord.trading.model.RsiAlert;
import ru.ingvord.trading.service.Bybit;
import ru.ingvord.trading.service.RsiCalculator;
import ru.ingvord.trading.service.TelegramBotCommandService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static ru.ingvord.trading.fixtures.FakeHttp.query;
import static ru.ingvord.trading.repository.AlertRepository.ALERT_STATUS_ACTIVE;
import static ru.ingvord.trading.repository.AlertRepository.ALERT_STATUS_RSI_ACTIVE;

/**
 * /rsi end to end through the real handlers (webhook JSON in, Telegram messages out) on synthesised
 * Bybit data. Offline by default; with -Ptelegram the same messages are delivered to the real bot.
 */
class RsiScenarioTest {
    private static final String SECRET = "s3cret";

    private final long chatA = TelegramEnv.chatId();
    private final long chatB = chatA + 1;

    private FakeHttp http;
    private InMemoryAlertRepository repo;
    private TelegramWebhookHandler webhook;
    private TelegramPriceBot bot;
    private Context context;

    @BeforeEach
    void setUp() throws Exception {
        http = FakeHttp.fromEnv();
        repo = new InMemoryAlertRepository();
        context = TestContext.create();

        var bybit = new Bybit(http.client);
        var commands = new TelegramBotCommandService(bybit, bybit, repo);
        webhook = new TelegramWebhookHandler(http.client, commands, TelegramEnv.token(), SECRET);
        bot = new TelegramPriceBot(bybit, bybit, repo, TelegramEnv.token(), http.client);
    }

    @AfterEach
    void noTelegramRejections() {
        assertEquals(List.of(), http.telegramFailures, "Telegram rejected a message");
    }

    // ---------------------------------------------------------------- webhook commands

    @Test
    void s1_create_replies_withCurrentRsiAndId() {
        http.header("Scenario 1: /rsi btc 4h");

        String reply = send(chatA, "/rsi btc 4h");

        var expected = RsiCalculator.wilder(CandleFixtures.column(CandleFixtures.recordedKlines(), 4), 14);
        assertTrue(reply.contains("RSI Alert Created"), reply);
        assertTrue(reply.contains(String.format("%.2f", expected)), reply);
        assertTrue(http.bybitCalls.get(0).toString().contains("symbol=BTCUSDT&interval=240"));

        var alert = onlyRsiAlert();
        assertEquals("btc", alert.symbol());
        assertEquals("4h", alert.interval());
        assertEquals(14, alert.period());
        assertEquals("close", alert.seriesType());
        assertEquals(RsiAlert.zoneOf(expected), alert.lastZone());
        assertEquals(ALERT_STATUS_RSI_ACTIVE, alert.status());
        assertTrue(reply.contains(alert.alertId().substring(0, 8)), "reply must contain the short id");
    }

    @Test
    void s2_create_withPeriodAndSeries() {
        http.header("Scenario 2: /rsi eth 1h 21 high");

        String reply = send(chatA, "/rsi eth 1h 21 high");

        var expected = RsiCalculator.wilder(CandleFixtures.column(CandleFixtures.recordedKlines(), 2), 21);
        assertTrue(reply.contains(String.format("%.2f", expected)), reply);
        String uri = http.bybitCalls.get(0).toString();
        assertTrue(uri.contains("symbol=ETHUSDT&interval=60&limit=210"), uri);
        var alert = onlyRsiAlert();
        assertEquals(21, alert.period());
        assertEquals("high", alert.seriesType());
    }

    @Test
    void s3_list_and_remove() {
        http.header("Scenario 3: /rsi list and /rsi rm");
        send(chatA, "/rsi btc 4h");
        send(chatA, "/rsi eth 1h");
        String firstId = repo.rsiAlerts.keySet().iterator().next();

        String list = send(chatA, "/rsi");
        assertTrue(list.contains("BTC") && list.contains("ETH"), list);
        assertTrue(list.contains(firstId.substring(0, 8)), list);

        assertTrue(send(chatA, "/rsi rm " + firstId.substring(0, 8)).contains("removed"));
        assertEquals(1, repo.rsiAlerts.size());
        assertFalse(send(chatA, "/rsi").contains("BTC"));

        assertTrue(send(chatA, "/rsi rm zzzz").contains("not found"));
        assertEquals(1, repo.rsiAlerts.size());
    }

    @Test
    void s3b_remove_isScopedToOwner() {
        http.header("Scenario 3b: another user cannot remove my alert");
        send(chatA, "/rsi btc 4h");
        String id = repo.rsiAlerts.keySet().iterator().next();

        assertTrue(send(chatB, "/rsi rm " + id.substring(0, 8)).contains("not found"));
        assertEquals(1, repo.rsiAlerts.size());
    }

    @Test
    void s4_invalidInput() {
        http.header("Scenario 4: invalid input");

        assertTrue(send(chatA, "/rsi btc 5m").contains("Invalid RSI command"));
        assertTrue(send(chatA, "/rsi btc 4h 14 median").contains("Invalid RSI command"));
        assertTrue(send(chatA, "/rsi btc").contains("Invalid RSI command"));
        assertTrue(send(chatA, "/rsi btc 4h 1").contains("period must be between 2 and 100"));
        assertTrue(send(chatA, "/rsi btc 4h 101").contains("period must be between 2 and 100"));

        assertEquals(0, http.bybitCalls.size(), "invalid input must not hit Bybit");
        assertTrue(repo.rsiAlerts.isEmpty());
    }

    @Test
    void s5_perUserLimit() {
        http.header("Scenario 5: sixth alert hits the limit");
        for (String symbol : List.of("btc", "eth", "sol", "xrp", "doge")) {
            assertTrue(send(chatA, "/rsi " + symbol + " 4h").contains("RSI Alert Created"));
        }

        String reply = send(chatA, "/rsi ada 4h");

        assertTrue(reply.contains("limit reached"), reply);
        assertEquals(5, repo.rsiAlerts.size());
        // another user is unaffected
        assertTrue(send(chatB, "/rsi ada 4h").contains("RSI Alert Created"));
    }

    @Test
    void s6_unknownSymbol_isRejectedAndNotStored() {
        http.header("Scenario 6: /rsi zzzz 1d (not on Bybit linear or spot)");
        http.bybit(uri -> new FakeHttp.Stub(200, CandleFixtures.invalidSymbolJson()));

        String reply = send(chatA, "/rsi zzzz 1d");

        assertTrue(reply.contains("Failed to create RSI alert"), reply);
        assertEquals(List.of("linear", "spot"), http.bybitCalls.stream().map(u -> query(u, "category")).toList());
        assertTrue(repo.rsiAlerts.isEmpty());
    }

    @Test
    void s7_priceAlertCommands_ignoreRsiAlerts() {
        http.header("Scenario 7: /list and /clear leave RSI alerts alone");
        send(chatA, "/rsi btc 4h");
        assertTrue(send(chatA, "/alert btc 50000 70000").contains("Alert Created"));

        String list = send(chatA, "/list");
        assertTrue(list.contains("Range"), list);
        assertFalse(list.contains("RSI"), list);

        assertTrue(send(chatA, "/clear").contains("Cleared 1 alerts"));
        assertEquals(1, repo.rsiAlerts.size(), "/clear must not delete RSI alerts");
        assertTrue(send(chatA, "/rsi").contains("BTC"));
    }

    @Test
    void s7b_wrongSecret_isRejected() {
        http.header("Scenario 7b: webhook with a wrong secret");
        var event = APIGatewayV2HTTPEvent.builder()
                .withBody(update(chatA, "/rsi btc 4h"))
                .withHeaders(Map.of("X-Telegram-Bot-Api-Secret-Token", "wrong"))
                .build();

        assertEquals(403, webhook.handleRequest(event, context).getStatusCode());
        assertTrue(repo.rsiAlerts.isEmpty());
        assertTrue(http.telegram.isEmpty());
    }

    // ---------------------------------------------------------------- scheduled checker

    @Test
    void s8_notifiesOnZoneEntry_notOnEveryRun() {
        http.header("Scenario 8: normal → overbought → overbought → normal → oversold");
        var alert = rsiAlert(chatA, "btc", "4h", 14, "close", RsiAlert.ZONE_NORMAL);
        var series = List.of(
                CandleFixtures.sideways(200, 1), CandleFixtures.rally(200, 1), CandleFixtures.rally(200, 2),
                CandleFixtures.sideways(200, 2), CandleFixtures.selloff(200, 1));
        var run = new AtomicInteger();
        http.bybit(uri -> new FakeHttp.Stub(200, CandleFixtures.klineJson("BTCUSDT", series.get(run.get()))));

        checkRun(run, 0);
        assertEquals(0, http.telegram.size(), "run 1: still normal");

        checkRun(run, 1);
        assertEquals(1, http.telegram.size(), "run 2: entered overbought");
        String overbought = http.telegram.get(0).text();
        assertTrue(overbought.contains("🔥") && overbought.contains("BTC 4h RSI(14)") && overbought.contains("overbought"), overbought);
        assertEquals(RsiAlert.ZONE_OVERBOUGHT, repo.rsiAlerts.get(alert.alertId()).lastZone());

        checkRun(run, 2);
        assertEquals(1, http.telegram.size(), "run 3: still overbought, no repeat");

        checkRun(run, 3);
        assertEquals(1, http.telegram.size(), "run 4: back to normal, silent re-arm");
        assertEquals(RsiAlert.ZONE_NORMAL, repo.rsiAlerts.get(alert.alertId()).lastZone());

        checkRun(run, 4);
        assertEquals(2, http.telegram.size(), "run 5: entered oversold");
        String oversold = http.telegram.get(1).text();
        assertTrue(oversold.contains("🧊") && oversold.contains("oversold"), oversold);
        assertEquals(RsiAlert.ZONE_OVERSOLD, repo.rsiAlerts.get(alert.alertId()).lastZone());
    }

    @Test
    void s9_createdInsideZone_doesNotFireImmediately() {
        http.header("Scenario 9: alert created while RSI is already overbought");
        http.bybitKlines(CandleFixtures.klineJson("BTCUSDT", CandleFixtures.rally(200, 3)));

        String reply = send(chatA, "/rsi btc 4h");
        assertTrue(reply.contains("RSI Alert Created"));
        assertEquals(RsiAlert.ZONE_OVERBOUGHT, onlyRsiAlert().lastZone());

        bot.handleRequest(null, context);

        assertEquals(1, http.telegram.size(), "only the creation reply, no alert");
    }

    @Test
    void s10_identicalRequestsShareOneBybitCall_andMessagesAreCombinedPerChat() {
        http.header("Scenario 10: grouping (2 chats, 3 alerts, 2 distinct series)");
        rsiAlert(chatA, "btc", "4h", 14, "close", RsiAlert.ZONE_NORMAL);
        rsiAlert(chatB, "btc", "4h", 14, "close", RsiAlert.ZONE_NORMAL);
        rsiAlert(chatA, "eth", "1h", 14, "close", RsiAlert.ZONE_NORMAL);
        http.bybit(uri -> new FakeHttp.Stub(200, CandleFixtures.klineJson("X", CandleFixtures.rally(200, 4))));

        bot.handleRequest(null, context);

        assertEquals(2, http.bybitCalls.size(), "btc 4h shared by two chats → one call");
        assertEquals(2, http.telegram.size(), "one combined message per chat");
        var forA = messageTo(chatA);
        var forB = messageTo(chatB);
        assertTrue(forA.contains("BTC 4h") && forA.contains("ETH 1h"), forA);
        assertTrue(forB.contains("BTC 4h") && !forB.contains("ETH"), forB);
    }

    @Test
    void s11_bybitFailureForOneSeries_doesNotBlockOthers() {
        http.header("Scenario 11: Bybit fails for BTC, ETH still notifies");
        var btc = rsiAlert(chatA, "btc", "4h", 14, "close", RsiAlert.ZONE_NORMAL);
        var eth = rsiAlert(chatA, "eth", "4h", 14, "close", RsiAlert.ZONE_NORMAL);
        http.bybit(uri -> uri.toString().contains("symbol=BTCUSDT")
                ? new FakeHttp.Stub(500, "boom")
                : new FakeHttp.Stub(200, CandleFixtures.klineJson("ETHUSDT", CandleFixtures.rally(200, 5))));

        bot.handleRequest(null, context);

        assertEquals(1, http.telegram.size());
        assertTrue(http.telegram.get(0).text().contains("ETH 4h"));
        assertEquals(RsiAlert.ZONE_NORMAL, repo.rsiAlerts.get(btc.alertId()).lastZone(), "failed series keeps its state");
        assertEquals(RsiAlert.ZONE_OVERBOUGHT, repo.rsiAlerts.get(eth.alertId()).lastZone());
    }

    @Test
    void s12_rsiFailure_doesNotBreakPriceAlerts() {
        http.header("Scenario 12: price alert fires even though the kline endpoint is down");
        priceAlert(chatA, "btc", "90", "110");
        rsiAlert(chatA, "btc", "4h", 14, "close", RsiAlert.ZONE_NORMAL);
        http.bybit(uri -> uri.getPath().endsWith("/kline")
                ? new FakeHttp.Stub(500, "down")
                : new FakeHttp.Stub(200, CandleFixtures.tickersJson("linear", Map.of("BTCUSDT", "120"))));

        bot.handleRequest(null, context);

        assertEquals(1, http.telegram.size());
        assertTrue(http.telegram.get(0).text().contains("Price Alert"), http.telegram.get(0).text());
    }

    @Test
    void s13_priceAndRsiAlertsInOneRun() {
        http.header("Scenario 13: price alert + RSI alert in the same run");
        priceAlert(chatA, "btc", "90", "110");
        rsiAlert(chatA, "btc", "4h", 14, "close", RsiAlert.ZONE_NORMAL);
        http.bybit(uri -> uri.getPath().endsWith("/kline")
                ? new FakeHttp.Stub(200, CandleFixtures.klineJson("BTCUSDT", CandleFixtures.selloff(200, 6)))
                : new FakeHttp.Stub(200, CandleFixtures.tickersJson("linear", Map.of("BTCUSDT", "120"))));

        bot.handleRequest(null, context);

        assertEquals(2, http.telegram.size());
        assertTrue(http.telegram.stream().anyMatch(m -> m.text().contains("Price Alert")));
        assertTrue(http.telegram.stream().anyMatch(m -> m.text().contains("oversold")));
    }

    // ---------------------------------------------------------------- stocks, ETFs and symbol resolution

    @Test
    void s14_stockEtfRsi_usesLinear() {
        http.header("Scenario 14: /rsi qqq 1h (ETF perpetual, linear only)");

        String reply = send(chatA, "/rsi qqq 1h");

        var expected = RsiCalculator.wilder(CandleFixtures.column(CandleFixtures.recordedQqqKlines(), 4), 14);
        assertTrue(reply.contains("QQQ") && reply.contains(String.format("%.2f", expected)), reply);
        assertTrue(http.bybitCalls.get(0).toString().contains("category=linear&symbol=QQQUSDT&interval=60"));
        assertEquals(1, http.bybitCalls.size());
    }

    @Test
    void s15_priceOfStockEtf() {
        http.header("Scenario 15: /price qqq");

        String reply = send(chatA, "/price qqq");

        assertTrue(reply.contains("QQQ") && reply.contains("$638.40"), reply);
        assertEquals(1, http.bybitCalls.size(), "one linear ticker call, no spot call");
    }

    @Test
    void s16_priceOfSpotOnlySymbol_fallsBackToSpot() {
        http.header("Scenario 16: /price aaplx (tokenised stock, spot only)");

        String reply = send(chatA, "/price aaplx");

        assertTrue(reply.contains("$358.17"), reply);
        assertEquals(List.of("linear", "spot"), http.bybitCalls.stream().map(u -> query(u, "category")).toList());
    }

    @Test
    void s17_unknownSymbol_isRejected() {
        http.header("Scenario 17: /price zzzz and /alert zzzz");

        assertTrue(send(chatA, "/price zzzz").contains("Failed to get price"));
        assertTrue(send(chatA, "/alert zzzz 1 2").contains("Failed to create alert"));
        assertTrue(repo.priceAlerts.isEmpty());
    }

    @Test
    void s18_createStockAlert_andCheckerFiresIt() {
        http.header("Scenario 18: /alert aapl 300 340 → checker fires (AAPL perp is 356.76)");

        String reply = send(chatA, "/alert aapl 300 340");
        assertTrue(reply.contains("Alert Created") && reply.contains("356.76"), reply);
        assertEquals("aapl", repo.priceAlerts.values().iterator().next().symbol());

        http.reset();
        bot.handleRequest(null, context);

        assertEquals(1, http.bybitCalls.size(), "one linear snapshot prices the whole run");
        assertEquals(1, http.telegram.size());
        String alert = http.telegram.get(0).text();
        assertTrue(alert.contains("AAPL has risen to $356.76") && alert.contains("$340.00"), alert);
    }

    @Test
    void s19_legacyStoredSymbols_stillResolve() {
        http.header("Scenario 19: alerts stored in the Twelve Data / CoinGecko era (btc/usd, bitcoin, matic)");
        priceAlert(chatA, "btc/usd", "1", "1000");
        priceAlert(chatA, "bitcoin", "1", "1000");
        priceAlert(chatA, "matic", "0.01", "0.05");

        bot.handleRequest(null, context);

        assertEquals(1, http.bybitCalls.size(), "all three are on linear");
        assertEquals(1, http.telegram.size(), "one combined message");
        String message = http.telegram.get(0).text();
        assertEquals(3, message.split("has risen to", -1).length - 1, message);
        assertTrue(message.contains("BTC/USD has risen to $60,614.60"), message);
        assertTrue(message.contains("MATIC has risen to $0.08"), message);
    }

    // ---------------------------------------------------------------- helpers

    private void checkRun(AtomicInteger run, int index) {
        run.set(index);
        System.out.println("  --- checker run " + (index + 1));
        bot.handleRequest(null, context);
    }

    /** Sends a Telegram update through the real webhook handler and returns the single reply. */
    private String send(long chatId, String text) {
        int before = http.telegram.size();
        var event = APIGatewayV2HTTPEvent.builder()
                .withBody(update(chatId, text))
                .withHeaders(Map.of("X-Telegram-Bot-Api-Secret-Token", SECRET))
                .build();

        assertEquals(200, webhook.handleRequest(event, context).getStatusCode());
        assertEquals(before + 1, http.telegram.size(), "expected exactly one reply to: " + text);
        var reply = http.telegram.get(before);
        assertEquals(String.valueOf(chatId), reply.chatId());
        return reply.text();
    }

    private static String update(long chatId, String text) {
        return "{\"update_id\":1,\"message\":{\"chat\":{\"id\":" + chatId + "},\"text\":\"" + text + "\"}}";
    }

    private RsiAlert rsiAlert(long chatId, String symbol, String tf, int period, String series, String zone) {
        var alert = new RsiAlert(UUID.randomUUID().toString(), chatId, symbol, tf, period, series, zone,
                System.currentTimeMillis(), ALERT_STATUS_RSI_ACTIVE);
        repo.saveRsi(alert);
        return alert;
    }

    private PriceAlert priceAlert(long chatId, String symbol, String min, String max) {
        var alert = new PriceAlert(UUID.randomUUID().toString(), chatId, symbol,
                new BigDecimal(min), new BigDecimal(max), System.currentTimeMillis(), ALERT_STATUS_ACTIVE);
        repo.save(alert);
        return alert;
    }

    private RsiAlert onlyRsiAlert() {
        assertEquals(1, repo.rsiAlerts.size());
        return repo.rsiAlerts.values().iterator().next();
    }

    private String messageTo(long chatId) {
        return http.telegram.stream().filter(m -> m.chatId().equals(String.valueOf(chatId)))
                .map(FakeHttp.Sent::text).findFirst().orElseThrow();
    }
}
