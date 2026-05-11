package com.orders.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ordersOpenApi(
            @Value("${server.port:8081}") String serverPort) {
        return new OpenAPI()
                .info(new Info()
                        .title("ms-common-orders — API de órdenes")
                        .version("1.0")
                        .description("""
                                Microservicio de órdenes: creación con validación contra inventario (**ms-common-stock**) \
                                y listado paginado. Las rutas de integración con stock se configuran en \
                                `stock.service.*` en `application.yaml`.""")
                        .contact(new Contact()
                                .name("Equipo microservicios")
                                .email("soporte@example.com")))
                .addServersItem(new Server()
                        .url("http://localhost:" + serverPort)
                        .description("Entorno local"));
    }
}
