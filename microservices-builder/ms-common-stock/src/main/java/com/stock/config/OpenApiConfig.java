package com.stock.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI stockOpenApi(@Value("${server.port:8082}") String serverPort) {
        return new OpenAPI()
                .info(new Info()
                        .title("ms-common-stock — Inventario")
                        .version("1.0")
                        .description("""
                                API en **`/api/stock`**. Persistencia PostgreSQL (tabla `stock`, Flyway).

                                Los códigos por operación reflejan `StockService`: **400** validación de líneas/campos; \
                                **404** id inexistente; **409** conflicto de negocio (p. ej. descuento imposible, código duplicado).

                                **UI:** `/swagger-ui.html` · **OpenAPI:** `/v3/api-docs`.""")
                        .contact(new Contact()
                                .name("Equipo microservicios")
                                .email("soporte@example.com")))
                .externalDocs(new ExternalDocumentation()
                        .description("OpenAPI JSON")
                        .url("/v3/api-docs"))
                .addServersItem(new Server()
                        .url("http://localhost:" + serverPort)
                        .description("Local"));
    }
}
