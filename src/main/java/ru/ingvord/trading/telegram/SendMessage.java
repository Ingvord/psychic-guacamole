package ru.ingvord.trading.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public class SendMessage {

    private final String url;

    private final String chatId;
    private final String message;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    protected SendMessage(String botApiToken, String chatId, String message) {
        this.message = message;
        this.chatId = chatId;
        this.url = "https://api.telegram.org/bot" + botApiToken + "/sendMessage";
    }

    public void execute() throws IOException, InterruptedException {
            Map<String, Object> payload = Map.of(
                    "chat_id", chatId,
                    "text", message,
                    "parse_mode", "Markdown"
            );

            String jsonPayload = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
