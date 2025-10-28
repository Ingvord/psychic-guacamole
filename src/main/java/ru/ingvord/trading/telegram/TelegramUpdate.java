package ru.ingvord.trading.telegram;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public class TelegramUpdate {
    public final long id;
    public final long chatId;
    public final String text;

    public TelegramUpdate(long id, long chatId, String text) {
        this.id = id;
        this.chatId = chatId;
        this.text = text;
    }

    public static TelegramUpdate parse(String json) throws IOException {
        var objectMapper = new ObjectMapper();

        JsonNode update = objectMapper.readTree(json);
        if (!update.has("message")) {
            throw new JsonParseException("message is empty");
        }

        long id = update.get("update_id").asLong();

        JsonNode message = update.get("message");
        long chatId = message.get("chat").get("id").asLong();
        String text = message.has("text") ? message.get("text").asText() : "";



        return new TelegramUpdate(id, chatId, text);
    }

}
