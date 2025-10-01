package ru.ingvord.trading.service;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class CoingeckoTest {

    @Test
    void getCurrentPrice() throws Exception {
         var httpClient = mock(HttpClient.class);
         var response = mock(HttpResponse.class);
         doReturn(200).when(response).statusCode();
         doReturn("""
                 {
                   "bitcoin": {
                     "usd": 117414
                   }
                 }
                 """).when(response).body();
         doReturn(response).when(httpClient).send(any(), any());

         var instance = new Coingecko(httpClient);

         var result = instance.getCurrentPrice("bitcoin");

        System.out.println(result);
    }
}