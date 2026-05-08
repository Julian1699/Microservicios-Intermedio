package com.orders.service;

import java.util.List;
import java.util.UUID;

import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orders.dtos.DOrderRequest;
import com.orders.dtos.DOrderResponse;
import com.orders.mappers.OrderMapper;
import com.orders.model.Order;
import com.orders.repository.OrderRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;

    private final OrderMapper orderMapper = Mappers.getMapper(OrderMapper.class);

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public void createOrder(DOrderRequest dOrderRequest) {
        Order order = orderMapper.fromDto(dOrderRequest);
        order.setOrderNumber(UUID.randomUUID().toString());
        orderRepository.save(order);
        log.info("An order has been successfully created here: {}", order.getOrderNumber());
    }

    public Page<DOrderResponse> getAllOrders(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Order> orders = orderRepository.findAll(pageable);
        List<DOrderResponse> orderResponseList = orderMapper.toList(orders.getContent());
        return new PageImpl<>(orderResponseList, pageable, orders.getTotalElements());
    }

}