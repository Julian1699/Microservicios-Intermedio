package com.products.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.products.dtos.ProductRequest;
import com.products.dtos.ProductResponse;
import com.products.model.Product;
import com.products.repository.ProductRespository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ProductService {

    @Autowired
    private ProductRespository productRespository;

    public void createProduct(ProductRequest productRequest) {
        Product product = Product.builder()
                .name(productRequest.getName())
                .description(productRequest.getDescription())
                .price(productRequest.getPrice())
                .build();
        productRespository.save(product);
        log.info("A product has been successfully created here: {}", product.getName());
    }

    public List<ProductResponse> getAllProducts() {
        List<Product> products = productRespository.findAll();
        return products.stream().map(this::mapToProductReponse).collect(Collectors.toList());
    }

    public void updateProduct(String productId, ProductRequest productRequest) {
        Optional<Product> productOptional = productRespository.findById(productId);
        if (!productOptional.isPresent()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found with id: " + productId);
        }
        Product product = productOptional.get();
        product.setName(productRequest.getName());
        product.setDescription(productRequest.getDescription());
        product.setPrice(productRequest.getPrice());
        productRespository.save(product);
        log.info("A product has been successfully updated here: {}", product.getName());
    }

    public void deleteProduct(String productId) {
        Optional<Product> productOptional = productRespository.findById(productId);
        if (!productOptional.isPresent()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found with id: " + productId);
        }
        Product product = productOptional.get();
        productRespository.delete(product);
        log.info("A product has been successfully deleted here: {}", product.getName());
    }

    private ProductResponse mapToProductReponse(Product product) {
        return ProductResponse.builder()
                .productId(product.getProductId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .build();
    }

}
