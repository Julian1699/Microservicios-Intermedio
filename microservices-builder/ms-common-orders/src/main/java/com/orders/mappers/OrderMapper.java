package com.orders.mappers;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.orders.dtos.DOrderLineItems;
import com.orders.dtos.DOrderRequest;
import com.orders.dtos.DOrderResponse;
import com.orders.model.Order;
import com.orders.model.OrderLineItems;

@Component
public class OrderMapper {

    public Order fromDto(DOrderRequest dOrderRequest) {
        if (dOrderRequest == null) {
            return null;
        }
        List<OrderLineItems> orderLineItems = null;
        if (dOrderRequest.getOrderLineItemsList() != null) {
            orderLineItems = new ArrayList<>();
            for (DOrderLineItems dOrderLineItems : dOrderRequest.getOrderLineItemsList()) {
                OrderLineItems mappedOrderLine = fromDto(dOrderLineItems);
                if (mappedOrderLine != null) {
                    orderLineItems.add(mappedOrderLine);
                }
            }
        }
        return Order.builder()
                .orderLineItems(orderLineItems)
                .build();
    }

    public OrderLineItems fromDto(DOrderLineItems dOrderLineItems) {
        if (dOrderLineItems == null) {
            return null;
        }
        return OrderLineItems.builder()
                .code(dOrderLineItems.getCode())
                .description(dOrderLineItems.getDescription())
                .price(dOrderLineItems.getPrice())
                .quantity(dOrderLineItems.getQuantity())
                .build();
    }

    public DOrderResponse toDto(Order orderEntity) {
        if (orderEntity == null) {
            return null;
        }
        List<DOrderLineItems> dOrderLineItemsList = null;
        if (orderEntity.getOrderLineItems() != null) {
            dOrderLineItemsList = new ArrayList<>();
            for (OrderLineItems orderLineItemEntity : orderEntity.getOrderLineItems()) {
                dOrderLineItemsList.add(toDto(orderLineItemEntity));
            }
        }
        return DOrderResponse.builder()
                .orderId(orderEntity.getOrderId())
                .orderNumber(orderEntity.getOrderNumber())
                .orderLineItemsList(dOrderLineItemsList)
                .build();
    }

    public DOrderLineItems toDto(OrderLineItems orderLineItemEntity) {
        if (orderLineItemEntity == null) {
            return null;
        }
        return DOrderLineItems.builder()
                .orderLineItemId(orderLineItemEntity.getOrderLineItemId())
                .code(orderLineItemEntity.getCode())
                .description(orderLineItemEntity.getDescription())
                .price(orderLineItemEntity.getPrice())
                .quantity(orderLineItemEntity.getQuantity())
                .build();
    }

    public List<DOrderResponse> toList(List<Order> orderEntities) {
        List<DOrderResponse> dOrderResponses = new ArrayList<>();
        if (orderEntities == null) {
            return dOrderResponses;
        }
        for (Order orderEntity : orderEntities) {
            dOrderResponses.add(toDto(orderEntity));
        }
        return dOrderResponses;
    }

}
