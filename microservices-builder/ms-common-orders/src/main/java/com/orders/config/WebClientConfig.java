package com.orders.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

    /** Bean {@code stockRestClient}: cliente HTTP de bajo nivel hacia ms-common-stock (inyectado en {@link StockWebClient}). */
    @Bean(name = "stockRestClient")
    public WebClient stockRestClient(
            WebClient.Builder builder,
            @Value("${stock.service.base-url:http://localhost:8082}") String baseUrl,
            @Value("${stock.service.timeout-ms:10000}") long timeoutMilliseconds) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(timeoutMilliseconds));
        return builder
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

}
