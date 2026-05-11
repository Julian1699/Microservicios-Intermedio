package com.orders.dtos;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Schema(description = "Una línea de pedido: producto, cantidad y precio unitario.")
public class DOrderLineItems {

    @EqualsAndHashCode.Include
    @Schema(description = "Id de línea tras persistir (null en el request de creación).", example = "10", accessMode = Schema.AccessMode.READ_ONLY)
    private Long orderLineItemId;

    @Schema(description = "Código de producto (debe existir en inventario con cantidad suficiente).", example = "IPHONE-14-128", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    @Schema(description = "Texto libre del producto.", example = "Celular iPhone 14 128GB")
    private String description;

    @Schema(description = "Precio unitario en la moneda del negocio.", example = "3200000")
    private BigDecimal price;

    @Schema(description = "Unidades pedidas en esta línea; debe ser > 0.", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer quantity;

}
