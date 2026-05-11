package com.orders.dtos;

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

    private Integer quantity;

}
