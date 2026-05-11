package com.stock.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.stock.model.Stock;

public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findByCode(String code);

    List<Stock> findByCodeIn(Collection<String> codes);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Stock stock SET stock.quantity = stock.quantity - :quantity WHERE stock.code = :productCode AND stock.quantity >= :quantity")
    int decreaseQuantity(@Param("productCode") String productCode, @Param("quantity") int quantity);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Stock stock SET stock.quantity = stock.quantity + :quantity WHERE stock.code = :productCode")
    int increaseQuantity(@Param("productCode") String productCode, @Param("quantity") int quantity);

}
