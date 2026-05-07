package com.products.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.products.dtos.DProductRequest;
import com.products.dtos.DProductResponse;
import com.products.mappers.ProductMapper;
import com.products.model.Product;
import com.products.repository.ProductRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductMapper productMapper;

    public void createProduct(DProductRequest productRequest) {
        Product product = productMapper.fromDto(productRequest);
        productRepository.save(product);
        log.info("A product has been successfully created here: {}", product.getName());
    }

    public Page<DProductResponse> getAllProducts(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> products = productRepository.findAll(pageable);
        List<DProductResponse> productResponseList = productMapper.toList(products.getContent());
        return new PageImpl<>(productResponseList, pageable, products.getTotalElements());
    }

    public void updateProduct(String productId, DProductRequest productRequest) {
        Optional<Product> productOptional = productRepository.findById(productId);
        if (!productOptional.isPresent()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found with id: " + productId);
        }
        Product product = productOptional.get();
        productMapper.merge(productRequest, product);
        productRepository.save(product);
        log.info("A product has been successfully updated here: {}", product.getName());
    }

    public void deleteProduct(String productId) {
        Optional<Product> productOptional = productRepository.findById(productId);
        if (!productOptional.isPresent()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found with id: " + productId);
        }
        Product product = productOptional.get();
        productRepository.delete(product);
        log.info("A product has been successfully deleted here: {}", product.getName());
    }

}
