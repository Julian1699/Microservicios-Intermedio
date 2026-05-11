package com.orders.dtos;

import java.util.List;

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
public class DOrderResponse {

    @EqualsAndHashCode.Include
    private Long orderId;

    private String orderNumber;

    private List<DOrderLineItems> orderLineItemsList;

    /** Presente al crear una orden cuando se omitieron líneas por inventario (resto de líneas sí se guardaron). */
    private List<String> inventoryExclusions;

}