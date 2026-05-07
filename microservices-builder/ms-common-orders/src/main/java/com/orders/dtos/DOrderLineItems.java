package com.orders.dtos;

import java.math.BigDecimal;

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
public class DOrderLineItems {

    @EqualsAndHashCode.Include
    private Long orderLineItemId;

    private String code;

    private String description;

    private BigDecimal price;

    private Integer quantity;

}
