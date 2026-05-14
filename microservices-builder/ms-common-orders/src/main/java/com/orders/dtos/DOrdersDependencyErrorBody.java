package com.orders.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Formato JSON devuelto cuando falla la integración con ms-common-stock ({@link com.orders.exception.UpstreamDependencyException}).
 * Documentación Swagger alineada con {@link com.orders.web.OrdersExceptionHandler}.
 */
@Value
@Builder
@Jacksonized
@Schema(
        name = "OrdersDependencyError",
        description = """
        Cuerpo JSON ante **502**, **503** o **409** originados por la llamada HTTP a **ms-common-stock** \
        (servicio caído, error HTTP remoto o conflicto en inventario). Incluye **service** para identificar \
        la dependencia sin adivinar solo por `path`.""")
public class DOrdersDependencyErrorBody {

    @Schema(description = "Microservicio upstream que falló o respondió error.", example = "ms-common-stock")
    String service;

    @Schema(description = "Código HTTP.", example = "503")
    int status;

    @Schema(description = "Frase corta del estado HTTP.", example = "Service Unavailable")
    String error;

    @Schema(description = "Detalle del problema.", example = "No se pudo consultar inventario")
    String message;

    @Schema(description = "Ruta del endpoint en ms-common-orders que recibió la petición.", example = "/api/order/create")
    String path;

    @Schema(description = "Instante ISO-8601.", example = "2026-05-11T04:49:34.889+00:00")
    String timestamp;

}
