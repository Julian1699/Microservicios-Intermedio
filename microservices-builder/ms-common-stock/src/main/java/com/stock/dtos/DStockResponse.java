package com.stock.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "Respuesta de consulta por código (lista `/codes`).")
public class DStockResponse {

    @Schema(description = "Código de producto.")
    private String code;

    @Schema(description = "True si hay unidades disponibles.")
    private boolean inStock;

    /** Unidades en almacén; null si no aplica. */
    @Schema(description = "Unidades en almacén.")
    private Integer quantity;

}
