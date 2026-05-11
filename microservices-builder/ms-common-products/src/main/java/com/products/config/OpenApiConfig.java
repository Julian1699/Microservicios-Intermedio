package com.products.config;

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
    public OpenAPI productsOpenApi(@Value("${server.port:8080}") String serverPort) {
        return new OpenAPI()
                .info(new Info()
                        .title("ms-common-products — Catálogo")
                        .version("1.0")
                        .description("""
                                API REST sobre **MongoDB** (colección `product`). Base: **`/api/product`**.

                                Los códigos HTTP documentados en cada operación coinciden con lo que lanza el código \
                                (`ResponseStatusException` → 404) o Spring MVC (JSON ilegible → 400). \
                                No hay validación Bean Validation en controladores; no se declaran 400 “de negocio” inventados.

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
