package com.orders.dtos;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Schema(description = "Orden persistida: identificadores, líneas aceptadas y posibles exclusiones de inventario.")
public class DOrderResponse {

    @EqualsAndHashCode.Include
    @Schema(description = "Identificador interno de la orden en base de datos.", example = "1")
    private Long orderId;

    @Schema(description = "Número de orden único generado al crear.", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private String orderNumber;

    @Schema(description = "Líneas guardadas (solo las que pasaron validación de inventario).")
    private List<DOrderLineItems> orderLineItemsList;

    /**
     * Presente al crear una orden cuando se omitieron líneas por inventario (resto de líneas sí se guardaron).
     */
    @Schema(description = "Motivos por los que líneas del request no se incluyeron (solo en creación parcial).", nullable = true)
    private List<String> inventoryExclusions;

}