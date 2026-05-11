package com.orders.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.orders.dtos.DOrderRequest;
import com.orders.dtos.DOrderResponse;
import com.orders.service.OrderService;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DOrderResponse createOrder(@RequestBody DOrderRequest dOrderRequest) {
        return orderService.createOrder(dOrderRequest);
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public Page<DOrderResponse> orderResponseList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return orderService.getAllOrders(page, size);
    }

}