package com.stock.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.stock.dtos.DStockEntryResponse;
import com.stock.dtos.DStockQuantityLine;
import com.stock.dtos.DStockResponse;
import com.stock.dtos.DStockUpsertRequest;
import com.stock.model.Stock;
import com.stock.repository.StockRepository;

@Service
public class StockService {

    @Autowired
    private StockRepository stockRepository;

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

    @Transactional
    public Page<DStockEntryResponse> findAllEntries(Pageable pageable) {
        Page<Stock> page = stockRepository.findAll(pageable);
        List<DStockEntryResponse> content = new ArrayList<>(page.getNumberOfElements());
        for (Stock stock : page.getContent()) {
            content.add(toEntryResponse(stock));
        }
        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    @Transactional
    public DStockEntryResponse findEntryById(Long stockId) {
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Stock no encontrado con id: " + stockId));
        return toEntryResponse(stock);
    }

    @Transactional
    public DStockEntryResponse createEntry(DStockUpsertRequest request) {
        if (request == null || !StringUtils.hasText(request.getCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El código es obligatorio.");
        }
        if (request.getQuantity() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad es obligatoria al crear.");
        }
        if (request.getQuantity() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad no puede ser negativa.");
        }
        String code = request.getCode().trim();
        if (stockRepository.existsByCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe stock para el código: " + code);
        }
        Stock stock = Stock.builder()
                .code(code)
                .quantity(request.getQuantity())
                .build();
        return toEntryResponse(stockRepository.save(stock));
    }

    @Transactional
    public DStockEntryResponse updateEntry(Long stockId, DStockUpsertRequest request) {
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Stock no encontrado con id: " + stockId));
        if (request == null) {
            return toEntryResponse(stock);
        }
        if (StringUtils.hasText(request.getCode())) {
            String newCode = request.getCode().trim();
            if (!newCode.equals(stock.getCode()) && stockRepository.existsByCodeAndStockIdNot(newCode, stockId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ya existe otra fila con el código: " + newCode);
            }
            stock.setCode(newCode);
        }
        if (request.getQuantity() != null) {
            if (request.getQuantity() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad no puede ser negativa.");
            }
            stock.setQuantity(request.getQuantity());
        }
        return toEntryResponse(stockRepository.save(stock));
    }

    @Transactional
    public void deleteEntry(Long stockId) {
        if (!stockRepository.existsById(stockId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock no encontrado con id: " + stockId);
        }
        stockRepository.deleteById(stockId);
    }

    private static DStockEntryResponse toEntryResponse(Stock stock) {
        Integer quantityInWarehouse = stock.getQuantity();
        return DStockEntryResponse.builder()
                .stockId(stock.getStockId())
                .code(stock.getCode())
                .quantity(quantityInWarehouse)
                .inStock(quantityInWarehouse != null && quantityInWarehouse > 0)
                .build();
    }

}
