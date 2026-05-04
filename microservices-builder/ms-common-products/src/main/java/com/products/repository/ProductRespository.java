package com.products.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.products.model.Product;

public interface ProductRespository extends MongoRepository<Product, String> {
}
