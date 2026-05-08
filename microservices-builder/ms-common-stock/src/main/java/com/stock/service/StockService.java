package com.stock.service;

import jakarta.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.stock.model.Stock;
import com.stock.repository.StockRepository;

@Service
public class StockService {

    @Autowired
    private StockRepository stockRepository;

    @Transactional
    public Boolean isInStock(String code) {
        return stockRepository.findByCode(code).isPresent();
    }

}
