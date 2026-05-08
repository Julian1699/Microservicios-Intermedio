package com.orders.mappers;

import static org.mapstruct.ReportingPolicy.IGNORE;

import java.util.ArrayList;
import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.orders.dtos.DOrderLineItems;
import com.orders.dtos.DOrderRequest;
import com.orders.dtos.DOrderResponse;
import com.orders.model.Order;
import com.orders.model.OrderLineItems;

@Mapper(unmappedTargetPolicy = IGNORE)
public interface OrderMapper {

    @Mapping(target = "orderId", ignore = true)
    @Mapping(target = "orderNumber", ignore = true)
    @Mapping(target = "orderLineItems", source = "orderLineItemsList")
    Order fromDto(DOrderRequest dto);

    @Mapping(target = "orderLineItemId", ignore = true)
    OrderLineItems fromDto(DOrderLineItems dto);

    @Mapping(target = "orderLineItemsList", source = "orderLineItems")
    DOrderResponse toDto(Order entity);

    DOrderLineItems toDto(OrderLineItems entity);

    default List<DOrderResponse> toList(List<Order> list) {
        List<DOrderResponse> responseList = new ArrayList<>();
        if (list == null) {
            return responseList;
        }
        for (Order entity : list) {
            responseList.add(toDto(entity));
        }
        return responseList;
    }
}