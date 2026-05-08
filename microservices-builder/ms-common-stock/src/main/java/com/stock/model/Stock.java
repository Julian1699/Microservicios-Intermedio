package com.stock.model;

import static jakarta.persistence.GenerationType.SEQUENCE;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Entity
@Builder
@Table(name = "stock")
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class Stock {

    private static final String STOCK_ID_SEQ = "stock_id_seq";

    @Id
    @GeneratedValue(generator = STOCK_ID_SEQ, strategy = SEQUENCE)
    @SequenceGenerator(name = STOCK_ID_SEQ, sequenceName = STOCK_ID_SEQ, allocationSize = 1)
    private Long stockId;

    private String code;

    private Integer quantity;

}
