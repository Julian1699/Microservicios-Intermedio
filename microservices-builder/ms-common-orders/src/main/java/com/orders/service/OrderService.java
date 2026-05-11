package com.orders.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.orders.config.StockWebClient;
import com.orders.config.StockWebClient.StockEligibility;
import com.orders.dtos.DOrderRequest;
import com.orders.dtos.DOrderResponse;
import com.orders.mappers.OrderMapper;
import com.orders.model.Order;
import com.orders.model.OrderLineItems;
import com.orders.repository.OrderRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;

    private final OrderMapper orderMapper;

    private final StockWebClient stockWebClient;

    public OrderService(
            OrderRepository orderRepository,
            OrderMapper orderMapper,
            StockWebClient stockWebClient) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
        this.stockWebClient = stockWebClient;
    }

    public DOrderResponse createOrder(DOrderRequest dOrderRequest) {
        Order order = orderMapper.fromDto(dOrderRequest);
        order = order.toBuilder()
                .orderNumber(UUID.randomUUID().toString())
                .build();
        Map<String, Integer> requestedQuantityByProductCode = aggregateRequestedQuantity(order.getOrderLineItems());
        if (requestedQuantityByProductCode.isEmpty()) {
            log.warn("createOrder rechazada: sin códigos/cantidades válidas. orderNumber={}", order.getOrderNumber());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "La orden debe incluir códigos de producto y cantidad mayor a cero en cada línea.");
        }
        log.info("createOrder: validando inventario. orderNumber={} demandaPorCodigo={}",
        order.getOrderNumber(), requestedQuantityByProductCode);
        StockEligibility inventoryEligibility = stockWebClient.evaluateEligibility(requestedQuantityByProductCode);
        List<OrderLineItems> keptOrderLines = new ArrayList<>();
        for (OrderLineItems orderLineItem : order.getOrderLineItems()) {
            String productCode = orderLineItem.getCode() != null ? orderLineItem.getCode().trim() : "";
            if (!StringUtils.hasText(productCode)) {
                continue;
            }
            if (inventoryEligibility.eligibleCodes().contains(productCode)) {
                keptOrderLines.add(orderLineItem);
            }
        }
        if (keptOrderLines.isEmpty()) {
            String conflictDetail = String.join("; ", inventoryEligibility.skippedLineReasons());
            log.warn("createOrder rechazada: ninguna línea cumple inventario. orderNumber={} detail={}",
            order.getOrderNumber(), conflictDetail);
            throw new ResponseStatusException(HttpStatus.CONFLICT, conflictDetail);
        }
        if (!inventoryEligibility.skippedLineReasons().isEmpty()) {
            log.warn("createOrder: inventario parcial — líneas guardadas={}; exclusiones={}. orderNumber={}",
            keptOrderLines.size(), inventoryEligibility.skippedLineReasons(), order.getOrderNumber());
        }
        Map<String, Integer> quantitiesToDeductByProductCode = aggregateRequestedQuantity(keptOrderLines);
        order = order.toBuilder()
                .orderLineItems(keptOrderLines)
                .build();
        boolean inventoryDeducted = false;
        try {
            stockWebClient.deductQuantities(quantitiesToDeductByProductCode);
            inventoryDeducted = true;
            orderRepository.save(order);
        } catch (RuntimeException runtimeException) {
            if (inventoryDeducted) {
                try {
                    stockWebClient.restoreQuantities(quantitiesToDeductByProductCode);
                } catch (Exception restoreException) {
                    log.error("Compensación de inventario fallida tras error al guardar orden {}: {}",
                    order.getOrderNumber(), restoreException.getMessage(), restoreException);
                }
            }
            throw runtimeException;
        }
        log.info("createOrder: orden guardada e inventario descontado. orderNumber={} lineas={}",
        order.getOrderNumber(), keptOrderLines.size());
        DOrderResponse dOrderResponse = orderMapper.toDto(order);
        if (!inventoryEligibility.skippedLineReasons().isEmpty()) {
            dOrderResponse = dOrderResponse.toBuilder()
                    .inventoryExclusions(inventoryEligibility.skippedLineReasons())
                    .build();
        }
        return dOrderResponse;
    }

    /**
     * Suma cantidades por código (varias líneas pueden referirse al mismo producto).
     */
    private static Map<String, Integer> aggregateRequestedQuantity(List<OrderLineItems> orderLineItems) {
        Map<String, Integer> quantityByProductCode = new HashMap<>();
        if (orderLineItems == null) {
            return quantityByProductCode;
        }
        for (OrderLineItems orderLineItem : orderLineItems) {
            String productCode = orderLineItem.getCode() != null ? orderLineItem.getCode().trim() : "";
            if (!StringUtils.hasText(productCode)) {
                continue;
            }
            if (orderLineItem.getQuantity() == null || orderLineItem.getQuantity() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cada línea con código debe tener cantidad mayor a cero.");
            }
            Integer previousQuantity = quantityByProductCode.get(productCode);
            int lineQuantity = orderLineItem.getQuantity();
            if (previousQuantity == null) {
                quantityByProductCode.put(productCode, lineQuantity);
            } else {
                quantityByProductCode.put(productCode, previousQuantity + lineQuantity);
            }
        }
        return quantityByProductCode;
    }

    public Page<DOrderResponse> getAllOrders(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Order> orders = orderRepository.findAll(pageable);
        List<DOrderResponse> dOrderResponseList = orderMapper.toList(orders.getContent());
        return new PageImpl<>(dOrderResponseList, pageable, orders.getTotalElements());
    }

}
