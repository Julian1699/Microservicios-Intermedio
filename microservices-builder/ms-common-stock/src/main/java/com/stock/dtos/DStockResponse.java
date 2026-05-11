package com.stock.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DStockResponse {

    private String code;

    private boolean inStock;

    /** Unidades en almacén; null si no aplica. */
    private Integer quantity;

}
