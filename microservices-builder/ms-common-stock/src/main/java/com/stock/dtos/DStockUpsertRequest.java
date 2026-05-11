package com.stock.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Alta o actualización de una fila de stock.")
public class DStockUpsertRequest {

    @Schema(description = "Código de producto; obligatorio en alta.", example = "SKU-001")
    private String code;

    @Schema(description = "Unidades; obligatorio en alta.", example = "100")
    private Integer quantity;

}
