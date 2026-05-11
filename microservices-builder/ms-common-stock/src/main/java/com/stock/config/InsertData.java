package com.stock.config;

import static com.fasterxml.jackson.core.JsonGenerator.Feature.IGNORE_UNKNOWN;

import java.io.File;
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
        log.info("Start processing stock seed data");
        if (isProfileActive(TEST_DATA_PROFILE)) {
            List<Stock> fromCsv = generateInitialConfiguration(Stock.class, STOCK_CSV);
            for (Stock stock : fromCsv) {
                if (stockRepository.findByCode(stock.getCode()).isPresent()) {
                    continue;
                }
                try {
                    stockRepository.save(stock);
                } catch (Exception exception) {
                    log.warn("Can not create stock with code {}", stock.getCode(), exception);
                }
            }
        }
        log.info("Finish processing stock seed data");
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
        try {
            File file = new ClassPathResource(fileName).getFile();
            MappingIterator<T> readValues = CsvMapper.builder()
                    .configure(IGNORE_UNKNOWN, true)
                    .enable(CsvParser.Feature.TRIM_SPACES)
                    .build()
                    .readerFor(type)
                    .with(CsvSchema.emptySchema().withHeader())
                    .readValues(file);
            return readValues.readAll();
        } catch (Exception exception) {
            log.error("Error occurred while loading object list from file {}", fileName, exception);
        }
        return Collections.emptyList();
    }

}
