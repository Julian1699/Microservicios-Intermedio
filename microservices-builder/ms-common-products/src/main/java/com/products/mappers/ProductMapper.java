package com.products.mappers;

import static org.mapstruct.ReportingPolicy.IGNORE;

import java.util.ArrayList;
import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.products.dtos.DProductRequest;
import com.products.dtos.DProductResponse;
import com.products.model.Product;

@Mapper(unmappedTargetPolicy = IGNORE)
public interface ProductMapper {

    @Mapping(target = "productId", ignore = true)
    Product fromDto(DProductRequest dProductRequest);

    DProductResponse toDto(Product entity);

    @Mapping(target = "productId", ignore = true)
    Product merge(DProductRequest dProductRequest, @MappingTarget Product product);

    default List<DProductResponse> toList(List<Product> list) {
        List<DProductResponse> responseList = new ArrayList<>();
        if (list == null) {
            return responseList;
        }
        for (Product product : list) {
            responseList.add(this.toDto(product));
        }
        return responseList;
    }

}