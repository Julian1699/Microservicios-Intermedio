package com.stock.config;

import static com.fasterxml.jackson.core.JsonGenerator.Feature.IGNORE_UNKNOWN;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.stock.model.Stock;
import com.stock.repository.StockRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class InsertData {

    private static final String TEST_DATA_PROFILE = "test-data";

    private static final String STOCK_CSV = "csv/stock.csv";

    private final Environment environment;

    private final StockRepository stockRepository;

    public void insertData() {
        if (!isProfileActive(TEST_DATA_PROFILE)) {
            return;
        }
        List<Stock> fromCsv = generateInitialConfiguration(Stock.class, STOCK_CSV);
        int inserted = 0;
        for (Stock stock : fromCsv) {
            if (stock.getCode() == null || stock.getCode().isBlank()) {
                continue;
            }
            if (stockRepository.findByCode(stock.getCode()).isPresent()) {
                continue;
            }
            try {
                stockRepository.save(stock);
                inserted++;
            } catch (Exception exception) {
                log.warn("No se pudo insertar stock con código {}: {}", stock.getCode(), exception.getMessage());
            }
        }
        if (inserted > 0) {
            log.info("Datos de prueba: {} fila(s) insertadas desde {}", inserted, STOCK_CSV);
        }
    }

    private boolean isProfileActive(String profileName) {
        for (String profile : environment.getActiveProfiles()) {
            if (profileName.equals(profile)) {
                return true;
            }
        }
        return false;
    }

    private <T> List<T> generateInitialConfiguration(Class<T> type, String fileName) {
        ClassPathResource resource = new ClassPathResource(fileName);
        if (!resource.exists()) {
            log.error("No se encontró {} en classpath", fileName);
            return Collections.emptyList();
        }
        try (InputStream inputStream = resource.getInputStream()) {
            MappingIterator<T> readValues = CsvMapper.builder()
                    .configure(IGNORE_UNKNOWN, true)
                    .enable(CsvParser.Feature.TRIM_SPACES)
                    .build()
                    .readerFor(type)
                    .with(CsvSchema.emptySchema().withHeader())
                    .readValues(inputStream);
            return readValues.readAll();
        } catch (Exception exception) {
            log.error("Error al leer {}: {}", fileName, exception.getMessage(), exception);
        }
        return Collections.emptyList();
    }

}
