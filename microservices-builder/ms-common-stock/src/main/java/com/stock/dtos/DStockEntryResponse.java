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
@Schema(description = "Fila de inventario (clave surrogate `stockId` + código de producto único).")
public class DStockEntryResponse {

    @Schema(description = "Id interno de la fila en PostgreSQL.", example = "1")
    private Long stockId;

    @Schema(description = "Código de producto (único).", example = "SKU-001")
    private String code;

    @Schema(description = "Unidades en almacén.", example = "42")
    private Integer quantity;

    @Schema(description = "True si quantity > 0.", example = "true")
    private boolean inStock;

}
