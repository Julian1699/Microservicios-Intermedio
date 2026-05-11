package com.products.dtos;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Schema(
        name = "ProductResponse",
        description = """
                Representación de un producto devuelta por la API (listado, detalle). El **productId** es de solo lectura \
                y coincide con el `_id` almacenado en MongoDB.""")
public class DProductResponse {

    @Schema(
            description = "Identificador único del documento en MongoDB.",
            example = "674a1b2c3d4e5f6789012345",
            accessMode = Schema.AccessMode.READ_ONLY)
    private String productId;

    @Schema(description = "Nombre comercial.", example = "Auriculares Bluetooth ANC")
    private String name;

    @Schema(description = "Descripción detallada.")
    private String description;

    @Schema(
            description = "Precio unitario persistido.",
            example = "129990.00")
    private BigDecimal price;

}
