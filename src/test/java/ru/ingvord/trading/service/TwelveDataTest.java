package ru.ingvord.trading.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class TwelveDataTest {

    @Test
    void getPrices() throws IOException, InterruptedException {
        var client = mock(HttpClient.class);
        var response = mock(HttpResponse.class);

        doReturn(response).when(client).send(any(), any());
        doReturn(200).when(response).statusCode();
        doReturn("""
                {
                  "btc/usd": {
                    "price": "113815.02"
                  },
                  "aapl": {
                    "price": "268.95"
                  },
                  "s&p500": {
                    "code": 404,
                    "message": "**symbol** not found: S&P500. Please specify it correctly according to API Documentation.",
                    "status": "error",
                    "meta": {
                      "symbol": "btc/usd,aapl,s&p500",
                      "interval": "",
                      "exchange": ""
                    }
                  }
                }
                """).when(response).body();


        var instance = new TwelveData(client, "");

        var result = instance.getPrices(new HashSet<>(){{
            add("btc/usd");
            add("aapl");
            add("s&p500");
        }});

        assertTrue(result.containsKey("btc/usd"));
        assertTrue(result.containsValue(new BigDecimal("113815.0200")));

        assertTrue(result.containsKey("aapl"));
        assertTrue(result.containsValue(new BigDecimal("268.9500")));
    }
}