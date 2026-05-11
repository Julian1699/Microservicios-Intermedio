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
        name = "ProductRequest",
        description = """
                Cuerpo para **crear** o **actualizar** un producto. En el alta no envíe `productId` \
                (lo genera MongoDB). En actualización parcial puede omitir campos que no quiera cambiar \
                según la política del mapper.""")
public class DProductRequest {

    @Schema(
            description = "Nombre mostrado del artículo.",
            example = "Auriculares Bluetooth ANC")
    private String name;

    @Schema(
            description = "Texto libre con características, garantía, etc.",
            example = "Cancelación activa de ruido, batería 30 h, estuche incluido.")
    private String description;

    @Schema(
            description = """
                    Precio unitario en la moneda del negocio (sin formato de locale en JSON; ej. número decimal).""",
            example = "129990.00")
    private BigDecimal price;

}
