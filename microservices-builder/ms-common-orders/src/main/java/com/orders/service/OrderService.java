package com.orders.service;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderMapper orderMapper;

    public void createOrder(DOrderRequest orderRequest) {
        Order order = orderMapper.fromDto(orderRequest);
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