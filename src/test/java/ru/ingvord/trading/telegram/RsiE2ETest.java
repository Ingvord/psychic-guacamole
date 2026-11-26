package ru.ingvord.trading.telegram;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import ru.ingvord.trading.fixtures.FakeHttp;
import ru.ingvord.trading.fixtures.TelegramEnv;
import ru.ingvord.trading.fixtures.TestContext;
import ru.ingvord.trading.model.RsiAlert;
import ru.ingvord.trading.repository.AlertRepository;
import ru.ingvord.trading.service.Bybit;
import ru.ingvord.trading.service.TelegramBotCommandService;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real Bybit API + real Telegram bot + the real AlertRepository on dynamodb-local.
 * Run with: DYNAMODB_ENDPOINT=http://localhost:8000 TELEGRAM_BOT_TOKEN=... TEST_CHAT_ID=... mvn test -Pe2e
 */
@org.junit.jupiter.api.Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RsiE2ETest {
    private static final String TABLE = "price-alerts-e2e";
    private static final String SECRET = "e2e-secret";

    private static DynamoDbClient dynamo;

    private final long chat = TelegramEnv.chatId();
    private FakeHttp http;
    private AlertRepository repo;
    private Bybit bybit;
    private TelegramWebhookHandler webhook;
    private TelegramPriceBot bot;
    private Context context;

    @BeforeAll
    static void createTable() {
        String endpoint = System.getenv("DYNAMODB_ENDPOINT");
        if (endpoint == null || !(URI.create(endpoint).getHost().equals("localhost") || URI.create(endpoint).getHost().equals("127.0.0.1"))) {
            throw new IllegalStateException("e2e must run against dynamodb-local (DYNAMODB_ENDPOINT=http://localhost:8000), got: " + endpoint);
        }
        dynamo = DynamoDbClient.builder()
                .region(Region.EU_CENTRAL_1)
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("x", "x")))
                .build();

        try {
            dynamo.deleteTable(DeleteTableRequest.builder().tableName(TABLE).build());
        } catch (ResourceNotFoundException ignored) {
            // fresh start
        }
        dynamo.createTable(CreateTableRequest.builder()
                .tableName(TABLE)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(attr("alertId"), attr("chatId"), attr("status"))
                .keySchema(KeySchemaElement.builder().attributeName("alertId").keyType(KeyType.HASH).build())
                .globalSecondaryIndexes(gsi("ChatIdIndex", "chatId"), gsi("StatusIndex", "status"))
                .build());
    }

    @AfterAll
    static void dropTable() {
        if (dynamo != null) {
            dynamo.deleteTable(DeleteTableRequest.builder().tableName(TABLE).build());
            dynamo.close();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        for (var item : dynamo.scan(ScanRequest.builder().tableName(TABLE).build()).items()) {
            dynamo.deleteItem(DeleteItemRequest.builder().tableName(TABLE)
                    .key(Map.of("alertId", item.get("alertId"))).build());
        }

        http = FakeHttp.live();
        repo = new AlertRepository(TABLE, dynamo);
        bybit = new Bybit(http.client);
        context = TestContext.create();

        var commands = new TelegramBotCommandService(bybit, bybit, repo);
        webhook = new TelegramWebhookHandler(http.client, commands, TelegramEnv.token(), SECRET);
        bot = new TelegramPriceBot(bybit, bybit, repo, TelegramEnv.token(), http.client);
    }

    @AfterEach
    void noTelegramRejections() {
        assertEquals(List.of(), http.telegramFailures, "Telegram rejected a message");
    }

    @Test
    @Order(1)
    void createListRemove_againstRealBybitAndDynamo() throws Exception {
        http.header("E2E 1: /rsi btc 4h against the real Bybit API");

        String reply = send("/rsi btc 4h");

        assertTrue(reply.contains("RSI Alert Created"), reply);
        var item = onlyItem();
        assertEquals("RSI_ACTIVE", item.get("status").s());
        assertEquals("4h", item.get("interval").s());
        assertEquals("btc", item.get("symbol").s());

        // the data is live: the newest candle must be the one currently forming
        long newestCandleStart = newestBybitCandleStart("BTCUSDT", "240");
        assertTrue(System.currentTimeMillis() - newestCandleStart < Duration.ofHours(4).plusMinutes(1).toMillis(),
                "newest 4h candle is stale: " + newestCandleStart);

        BigDecimal rsi = bybit.getRsi("btc", "4h", 14, "close");
        assertTrue(rsi.signum() >= 0 && rsi.compareTo(new BigDecimal("100")) <= 0);
        http.header(String.format("E2E: BTCUSDT 4h RSI(14) = %.2f (live candle) — compare with the 4h RSI(14) on the Bybit chart", rsi));

        // RSI alerts must stay invisible to the price-alert commands (real ChatIdIndex filter)
        assertTrue(send("/list").contains("No Active Alerts"));
        assertTrue(send("/rsi").contains("BTC"));

        String id = item.get("alertId").s();
        assertTrue(send("/rsi rm " + id.substring(0, 8)).contains("removed"));
        assertEquals(0, dynamo.scan(ScanRequest.builder().tableName(TABLE).build()).count());
    }

    @Test
    @Order(2)
    void forcedZoneEntry_sendsExactlyOneRealNotification() {
        http.header("E2E 2: forced RSI notification (RSI(2) on the first pair currently in a zone)");

        String symbol = null;
        String tf = null;
        outer:
        for (String s : List.of("btc", "eth", "sol", "xrp", "doge")) {
            for (String t : List.of("15m", "1h")) {
                try {
                    if (!RsiAlert.ZONE_NORMAL.equals(RsiAlert.zoneOf(bybit.getRsi(s, t, 2, "close")))) {
                        symbol = s;
                        tf = t;
                        break outer;
                    }
                } catch (Exception e) {
                    System.out.println("  scan " + s + " " + t + " failed: " + e.getMessage());
                }
            }
        }
        Assumptions.assumeTrue(symbol != null, "no pair has RSI(2) in a zone right now");

        send("/rsi " + symbol + " " + tf + " 2");
        var alert = repo.findRsiByChatId(chat).get(0);
        repo.updateRsiZone(alert.alertId(), RsiAlert.ZONE_NORMAL);
        http.reset();

        bot.handleRequest(null, context);

        String marker = symbol.toUpperCase() + " " + tf + " RSI(2)";
        long notifications = http.telegram.stream().filter(m -> m.text().contains(marker)).count();
        Assumptions.assumeTrue(notifications > 0 || !isStillInZone(symbol, tf),
                "RSI(2) left its zone between creation and check; rerun");
        assertEquals(1, notifications, "expected exactly one notification for " + marker);
        assertNotEquals(RsiAlert.ZONE_NORMAL, repo.findRsiByChatId(chat).get(0).lastZone());

        int before = http.telegram.size();
        bot.handleRequest(null, context);
        assertEquals(before, http.telegram.size(), "same zone on the next run must stay silent");

        assertTrue(send("/rsi rm " + alert.alertId().substring(0, 8)).contains("removed"));
    }

    @Test
    @Order(3)
    void stockAndCryptoPrices_andPriceAlert_againstRealBybit() throws Exception {
        http.header("E2E 3: real prices for a stock ETF perpetual (QQQ) and crypto (BTC), plus a price alert");

        var prices = bybit.getPrices(Set.of("qqq", "btc"));
        assertEquals(Set.of("qqq", "btc"), prices.keySet());
        BigDecimal qqq = prices.get("qqq");
        assertTrue(qqq.signum() > 0 && prices.get("btc").signum() > 0);

        // the single-symbol endpoint agrees with the snapshot (same market, seconds apart)
        BigDecimal single = bybit.getCurrentPrice("qqq");
        assertTrue(single.subtract(qqq).abs().divide(qqq, 6, java.math.RoundingMode.HALF_UP).compareTo(new BigDecimal("0.01")) < 0,
                "snapshot " + qqq + " vs single " + single);
        assertTrue(send("/price qqq").contains("QQQ"));
        http.header(String.format("E2E: QQQ = %s, BTC = %s (Bybit linear) — compare with the Bybit app", qqq.toPlainString(), prices.get("btc").toPlainString()));

        // a range just above the current price: the checker must report "dropped below" through real DynamoDB
        BigDecimal min = qqq.multiply(new BigDecimal("1.05")).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal max = qqq.multiply(new BigDecimal("1.5")).setScale(2, java.math.RoundingMode.HALF_UP);
        assertTrue(send("/alert qqq " + min + " " + max).contains("Alert Created"));
        http.reset();

        bot.handleRequest(null, context);

        assertEquals(1, http.telegram.stream().filter(m -> m.text().contains("QQQ has dropped to")).count());

        // price alerts repeat on every run by design; clean up
        String id = repo.findByChatId(chat).get(0).alertId();
        assertTrue(send("/remove " + id.substring(0, 8)).contains("removed"));
    }

    // ---------------------------------------------------------------- helpers

    private boolean isStillInZone(String symbol, String tf) {
        try {
            return !RsiAlert.ZONE_NORMAL.equals(RsiAlert.zoneOf(bybit.getRsi(symbol, tf, 2, "close")));
        } catch (Exception e) {
            return false;
        }
    }

    private String send(String text) {
        int before = http.telegram.size();
        var event = APIGatewayV2HTTPEvent.builder()
                .withBody("{\"update_id\":1,\"message\":{\"chat\":{\"id\":" + chat + "},\"text\":\"" + text + "\"}}")
                .withHeaders(Map.of("X-Telegram-Bot-Api-Secret-Token", SECRET))
                .build();
        assertEquals(200, webhook.handleRequest(event, context).getStatusCode());
        assertEquals(before + 1, http.telegram.size(), "expected exactly one reply to: " + text);
        return http.telegram.get(before).text();
    }

    private static Map<String, AttributeValue> onlyItem() {
        var items = dynamo.scan(ScanRequest.builder().tableName(TABLE).build()).items();
        assertEquals(1, items.size());
        return items.get(0);
    }

    private static long newestBybitCandleStart(String symbol, String interval) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(
                "https://api.bybit.com/v5/market/kline?category=spot&symbol=" + symbol + "&interval=" + interval + "&limit=1")).GET().build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        return new ObjectMapper().readTree(response.body()).path("result").path("list").get(0).get(0).asLong();
    }

    private static AttributeDefinition attr(String name) {
        return AttributeDefinition.builder().attributeName(name).attributeType(ScalarAttributeType.S).build();
    }

    private static GlobalSecondaryIndex gsi(String name, String hashKey) {
        return GlobalSecondaryIndex.builder()
                .indexName(name)
                .keySchema(KeySchemaElement.builder().attributeName(hashKey).keyType(KeyType.HASH).build())
                .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                .build();
    }
}
