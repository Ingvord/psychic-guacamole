package ru.ingvord.trading.telegram;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SendMessageTest {



    @Test
    void execute() throws  Exception {
        try {
            new SendMessage("","1136291781", "ping", mock(HttpClient.class)).execute();
        } catch (IOException|InterruptedException ignored) {

        }
    }
}