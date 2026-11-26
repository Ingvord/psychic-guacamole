package ru.ingvord.trading.fixtures;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * HttpClient that routes by host:
 * <ul>
 *   <li>api.bybit.com: a canned/synthesised response (or the real API when {@code realBybit})</li>
 *   <li>api.telegram.org: every sendMessage is captured and printed; when live and addressed to the
 *       live chat it is also delivered for real, and a non-200 from Telegram is recorded as a failure</li>
 * </ul>
 */
public class FakeHttp {
    public record Stub(int status, String body) {}
    public record Sent(String chatId, String text) {}

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public final HttpClient client = mock(HttpClient.class);
    public final List<Sent> telegram = new ArrayList<>();
    public final List<URI> bybitCalls = new ArrayList<>();
    public final List<String> telegramFailures = new ArrayList<>();

    private final boolean live;
    private final String liveChatId;
    private final String token;
    private final boolean realBybit;
    private final HttpClient real;
    private Function<URI, Stub> bybit = FakeHttp::defaultBybit;

    private FakeHttp(boolean live, String token, long liveChatId, boolean realBybit) {
        this.live = live;
        this.token = token;
        this.liveChatId = String.valueOf(liveChatId);
        this.realBybit = realBybit;
        this.real = (live || realBybit) ? HttpClient.newHttpClient() : null;
        try {
            doAnswer(inv -> route(inv.getArgument(0))).when(client).send(any(), any());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Synthesised Bybit data; Telegram real only with -Dtelegram.live=true. */
    public static FakeHttp fromEnv() {
        return new FakeHttp(TelegramEnv.live(), TelegramEnv.token(), TelegramEnv.chatId(), false);
    }

    /** Synthesised Bybit data, Telegram never leaves the JVM. */
    public static FakeHttp offline() {
        return new FakeHttp(false, "test-token", 1001L, false);
    }

    /** Real Bybit and real Telegram (calls are still recorded). */
    public static FakeHttp live() {
        return new FakeHttp(true, TelegramEnv.token(), TelegramEnv.chatId(), true);
    }

    /**
     * Default Bybit behaviour, shaped like the real API: kline returns recorded candles for any symbol
     * (QQQUSDT gets its own recording); tickers returns the recorded snapshot of the requested category,
     * filtered to one symbol when asked (empty list if it is listed in the other category, 10001 if unknown).
     */
    private static Stub defaultBybit(URI uri) {
        String symbol = query(uri, "symbol");
        if (uri.getPath().endsWith("/kline")) {
            return new Stub(200, "QQQUSDT".equals(symbol) ? CandleFixtures.recordedQqqKlines() : CandleFixtures.recordedKlines());
        }

        String snapshot = CandleFixtures.recordedTickers(query(uri, "category"));
        if (symbol == null) {
            return new Stub(200, snapshot);
        }
        boolean knownAnywhere = CandleFixtures.recordedTickers("linear").contains("\"" + symbol + "\"")
                || CandleFixtures.recordedTickers("spot").contains("\"" + symbol + "\"");
        return knownAnywhere
                ? new Stub(200, CandleFixtures.filterTickers(snapshot, symbol))
                : new Stub(200, CandleFixtures.invalidSymbolJson());
    }

    /** Query parameter of a request URI, or null. */
    public static String query(URI uri, String name) {
        for (String pair : uri.getQuery().split("&")) {
            if (pair.startsWith(name + "=")) return pair.substring(name.length() + 1);
        }
        return null;
    }

    public FakeHttp bybit(Function<URI, Stub> handler) {
        this.bybit = handler;
        return this;
    }

    public FakeHttp bybitKlines(String klineJson) {
        return bybit(uri -> new Stub(200, klineJson));
    }

    /** Scenario title: printed, and delivered to the live chat so it reads like a test report. */
    public void header(String text) {
        frame("SCENARIO", text);
        if (live) {
            try {
                var body = MAPPER.writeValueAsString(java.util.Map.of("chat_id", liveChatId, "text", "🧪 " + text));
                real.send(HttpRequest.newBuilder(URI.create("https://api.telegram.org/bot" + token + "/sendMessage"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                telegramFailures.add("header: " + e.getMessage());
            }
        }
    }

    public void reset() {
        telegram.clear();
        bybitCalls.clear();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private HttpResponse route(HttpRequest request) throws Exception {
        String host = request.uri().getHost();

        if ("api.bybit.com".equals(host)) {
            bybitCalls.add(request.uri());
            if (realBybit) {
                return real.send(request, HttpResponse.BodyHandlers.ofString());
            }
            return response(bybit.apply(request.uri()));
        }

        if ("api.telegram.org".equals(host)) {
            JsonNode payload = MAPPER.readTree(bodyOf(request));
            var sent = new Sent(payload.get("chat_id").asText(), payload.get("text").asText());
            telegram.add(sent);
            frame("TELEGRAM → chat " + sent.chatId(), sent.text());

            if (live && liveChatId.equals(sent.chatId())) {
                HttpResponse<String> actual = real.send(request, HttpResponse.BodyHandlers.ofString());
                if (actual.statusCode() != 200) {
                    telegramFailures.add(actual.statusCode() + " " + actual.body() + " for: " + sent.text());
                }
                return actual;
            }
            return response(new Stub(200, "{\"ok\":true}"));
        }

        throw new java.io.IOException("Unexpected host in test: " + host);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static HttpResponse response(Stub stub) {
        HttpResponse response = mock(HttpResponse.class);
        doReturn(stub.status()).when(response).statusCode();
        doReturn(stub.body()).when(response).body();
        return response;
    }

    private static String bodyOf(HttpRequest request) throws Exception {
        var out = new ByteArrayOutputStream();
        var done = new CompletableFuture<Void>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            @Override public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
            @Override public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                out.writeBytes(bytes);
            }
            @Override public void onError(Throwable t) { done.completeExceptionally(t); }
            @Override public void onComplete() { done.complete(null); }
        });
        done.get(5, TimeUnit.SECONDS);
        return out.toString(StandardCharsets.UTF_8);
    }

    private static void frame(String title, String text) {
        System.out.println("┌─ " + title + " ─────────────────────────────");
        text.lines().forEach(line -> System.out.println("│ " + line));
        System.out.println("└──────────────────────────────────────────────");
    }
}
