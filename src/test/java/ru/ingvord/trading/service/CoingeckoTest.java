package ru.ingvord.trading.service;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class CoingeckoTest {

    @Test
    void getCurrentPrice() throws Exception {
         var httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

         var instance = new Coingecko(httpClient);

         var result = instance.getCurrentPrice("bitcoin");

        System.out.println(result);
    }
}