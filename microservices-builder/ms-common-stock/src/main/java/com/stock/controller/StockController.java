package com.stock.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.stock.dtos.DStockQuantityLine;
import com.stock.dtos.DStockResponse;
import com.stock.service.StockService;

@RestController
@RequestMapping("/api/stock")
public class StockController {

    @Autowired
    private StockService stockService;

    @GetMapping("/{code}")
    public boolean isInStock(@PathVariable("code") String productCode) {
        return stockService.isInStock(productCode);
    }

    @GetMapping("/codes")
    @ResponseStatus(HttpStatus.OK)
    public List<DStockResponse> getStocksByCodes(@RequestParam("codes") List<String> productCodes) {
        return stockService.getStocksByCodes(productCodes);
    }

    @PostMapping("/deduct")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deduct(@RequestBody List<DStockQuantityLine> dStockQuantityLines) {
        stockService.deduct(dStockQuantityLines);
    }

    @PostMapping("/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(@RequestBody List<DStockQuantityLine> dStockQuantityLines) {
        stockService.restore(dStockQuantityLines);
    }

}
