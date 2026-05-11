package com.stock.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.stock.dtos.DStockQuantityLine;
import com.stock.dtos.DStockResponse;
import com.stock.model.Stock;
import com.stock.repository.StockRepository;

@Service
public class StockService {

    @Autowired
    private StockRepository stockRepository;

    @Transactional
    public Boolean isInStock(String productCode) {
        Optional<Stock> optionalStock = stockRepository.findByCode(productCode);
        if (optionalStock.isEmpty()) {
            return false;
        }
        Stock stock = optionalStock.get();
        Integer quantityInWarehouse = stock.getQuantity();
        return quantityInWarehouse != null && quantityInWarehouse > 0;
    }

    @Transactional
    public List<DStockResponse> getStocksByCodes(List<String> productCodes) {
        List<Stock> stocks = stockRepository.findByCodeIn(productCodes);
        List<DStockResponse> dStockResponses = new ArrayList<>(stocks.size());
        for (Stock stock : stocks) {
            Integer quantityInWarehouse = stock.getQuantity();
            DStockResponse dStockResponse = DStockResponse.builder()
                    .code(stock.getCode())
                    .quantity(quantityInWarehouse)
                    .inStock(quantityInWarehouse != null && quantityInWarehouse > 0)
                    .build();
            dStockResponses.add(dStockResponse);
        }
        return dStockResponses;
    }

    /**
     * Descuenta unidades de forma atómica por código. Toda la operación es transaccional.
     */
    @Transactional
    public void deduct(List<DStockQuantityLine> dStockQuantityLines) {
        Map<String, Integer> quantityByProductCode = mergeQuantities(dStockQuantityLines);
        for (Map.Entry<String, Integer> quantityEntry : quantityByProductCode.entrySet()) {
            int updatedRows = stockRepository.decreaseQuantity(quantityEntry.getKey(), quantityEntry.getValue());
            if (updatedRows == 0) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "No fue posible descontar stock para el código " + quantityEntry.getKey()
                        + " (sin existencias suficientes o código inexistente).");
            }
        }
    }

    /**
     * Devuelve unidades al inventario (compensación si falla la orden tras descontar).
     */
    @Transactional
    public void restore(List<DStockQuantityLine> dStockQuantityLines) {
        Map<String, Integer> quantityByProductCode = mergeQuantities(dStockQuantityLines);
        for (Map.Entry<String, Integer> quantityEntry : quantityByProductCode.entrySet()) {
            stockRepository.increaseQuantity(quantityEntry.getKey(), quantityEntry.getValue());
        }
    }

    private static Map<String, Integer> mergeQuantities(List<DStockQuantityLine> dStockQuantityLines) {
        if (dStockQuantityLines == null || dStockQuantityLines.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> quantityByProductCode = new HashMap<>();
        for (DStockQuantityLine dStockQuantityLine : dStockQuantityLines) {
            if (dStockQuantityLine.getCode() == null || dStockQuantityLine.getCode().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cada línea de movimiento debe incluir código.");
            }
            if (dStockQuantityLine.getQuantity() == null || dStockQuantityLine.getQuantity() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La cantidad a mover debe ser mayor que cero para el código " + dStockQuantityLine.getCode());
            }
            String productCode = dStockQuantityLine.getCode().trim();
            int lineQuantity = dStockQuantityLine.getQuantity();
            Integer previousQuantity = quantityByProductCode.get(productCode);
            if (previousQuantity == null) {
                quantityByProductCode.put(productCode, lineQuantity);
            } else {
                quantityByProductCode.put(productCode, previousQuantity + lineQuantity);
            }
        }
        return quantityByProductCode;
    }

}
