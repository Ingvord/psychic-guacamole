package ru.ingvord.trading.telegram;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class SendMessageTest {

    private HttpClient httpClient;


    @BeforeEach
    public void setup() throws IOException, InterruptedException {
        httpClient = mock(HttpClient.class);
        var response = mock(HttpResponse.class);
        doReturn(200).when(response).statusCode();
        doReturn(response).when(httpClient).send(any(), any());
    }

    @Test
    void execute() throws  Exception {
        try {
            new SendMessage("","1136291781", "ping", httpClient).execute();
        } catch (IOException|InterruptedException ignored) {

        }
    }
}