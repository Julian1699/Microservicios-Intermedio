package com.orders.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientRequestObservationConvention;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

    /**
     * Nombres de span cliente en Zipkin: prefijo {@code webclient} + método + ruta (p. ej. {@code webclient get /api/stock/query/codes}),
     * distinto de los spans servidor {@code http …} del mismo micro.
     */
    @Bean
    ClientRequestObservationConvention descriptiveClientRequestObservationConvention() {
        return new DescriptiveClientRequestObservationConvention();
    }

    /**
     * Cliente HTTP hacia ms-common-stock.
     * <p>
     * Aquí se centraliza el <strong>único</strong> timeout de red para ese servicio:
     * {@link HttpClient#responseTimeout(Duration)} con {@code stock.service.timeout-ms} del YAML.
     * {@link StockWebClient} no vuelve a leer el timeout: usa {@code Mono.block()} y deja que el transporte
     * cancele la petición cuando venza ese límite.
     */
    @Bean(name = "stockRestClient")
    @LoadBalanced
    public WebClient stockRestClient(
            WebClient.Builder builder,
            @Value("${stock.service.base-url:http://localhost:8082}") String stockServiceBaseUrl,
            @Value("${stock.service.timeout-ms:10000}") long stockServiceTimeoutMilliseconds) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(stockServiceTimeoutMilliseconds));
        return builder
                .baseUrl(stockServiceBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
