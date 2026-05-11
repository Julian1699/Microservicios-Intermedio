package com.orders.dtos;

import java.util.List;

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
@Schema(description = "Solicitud para crear una orden: lista de líneas con código de producto y cantidades.")
public class DOrderRequest {

    @Schema(description = "Líneas de la orden; al menos una debe tener código y cantidad > 0.", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<DOrderLineItems> orderLineItemsList;

}
