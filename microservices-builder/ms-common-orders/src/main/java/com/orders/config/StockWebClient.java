package com.orders.config;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import com.orders.dtos.DStockQuantityLine;
import com.orders.dtos.DStockResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Cliente de integración hacia {@code ms-common-stock}: usa el {@link WebClient} bean
 * {@code stockRestClient} de {@link WebClientConfig} (base URL, timeout de transporte).
 */
@Slf4j
@Component
public class StockWebClient {

    /**
     * Resultado de consultar inventario: códigos que pueden facturarse y motivos por los que otros quedan fuera.
     */
    public record StockEligibility(Set<String> eligibleCodes, List<String> skippedLineReasons) {}

    private static final String CODES_PATH = "/api/stock/codes";

    private static final String DEDUCT_PATH = "/api/stock/deduct";

    private static final String RESTORE_PATH = "/api/stock/restore";

    private final WebClient stockRestClient;

    private final long requestTimeOutMilliSeconds;

    public StockWebClient(
        @Qualifier("stockRestClient") WebClient stockRestClient,
        @Value("${stock.service.timeout-ms:10000}") long requestTimeOutMilliSeconds) {
        this.stockRestClient = stockRestClient;
        this.requestTimeOutMilliSeconds = requestTimeOutMilliSeconds;
    }

    /**
     * Consulta inventario y clasifica por código según la cantidad total solicitada (suma de líneas).
     */
    public StockEligibility evaluateEligibility(Map<String, Integer> requestedQuantityByProductCode) {
        if (requestedQuantityByProductCode == null || requestedQuantityByProductCode.isEmpty()) {
            return new StockEligibility(Set.of(), List.of());
        }
        List<String> productCodes = new ArrayList<>(requestedQuantityByProductCode.keySet());
        List<DStockResponse> dStockResponses = fetchStockRows(productCodes);
        Map<String, DStockResponse> dStockResponseByProductCode = indexStockByProductCode(dStockResponses);
        Set<String> eligibleProductCodes = new HashSet<>();
        List<String> skippedInventoryReasons = new ArrayList<>();
        for (Map.Entry<String, Integer> requestedQuantityEntry : requestedQuantityByProductCode.entrySet()) {
            String productCode = requestedQuantityEntry.getKey();
            int requestedQuantity = requestedQuantityEntry.getValue();
            DStockResponse dStockResponse = dStockResponseByProductCode.get(productCode);
            if (dStockResponse == null) {
                log.warn("Inventario: código '{}' sin registro en ms-common-stock", productCode);
                skippedInventoryReasons.add("Código sin registro en inventario: " + productCode);
                continue;
            }
            Integer quantityInWarehouse = dStockResponse.getQuantity();
            int availableQuantity = quantityInWarehouse != null ? quantityInWarehouse : 0;
            if (availableQuantity < requestedQuantity) {
                log.warn("Inventario: código '{}' stock insuficiente (solicitado {}, disponible {})",
                        productCode, requestedQuantity, availableQuantity);
                skippedInventoryReasons.add(
                    "Stock insuficiente para " + productCode + ": solicitado " + requestedQuantity
                    + ", disponible " + availableQuantity);
            } else {
                eligibleProductCodes.add(productCode);
            }
        }
        return new StockEligibility(eligibleProductCodes, skippedInventoryReasons);
    }

    /**
     * Descuenta unidades en ms-common-stock (transacción del lado stock).
     */
    public void deductQuantities(Map<String, Integer> quantitiesByProductCode) {
        if (quantitiesByProductCode == null || quantitiesByProductCode.isEmpty()) {
            return;
        }
        postWithoutResponseBody(DEDUCT_PATH, dStockQuantityLinesFromMap(quantitiesByProductCode), "deduct");
    }

    /**
     * Devuelve unidades al inventario si hubo que compensar tras un fallo al guardar la orden.
     */
    public void restoreQuantities(Map<String, Integer> quantitiesByProductCode) {
        if (quantitiesByProductCode == null || quantitiesByProductCode.isEmpty()) {
            return;
        }
        postWithoutResponseBody(RESTORE_PATH, dStockQuantityLinesFromMap(quantitiesByProductCode), "restore");
    }

    /**
     * Construye el cuerpo del POST hacia stock: una línea por código con su cantidad (builder explícito).
     */
    private static List<DStockQuantityLine> dStockQuantityLinesFromMap(Map<String, Integer> quantitiesByProductCode) {
        List<DStockQuantityLine> dStockQuantityLines = new ArrayList<>(quantitiesByProductCode.size());
        for (Map.Entry<String, Integer> quantityEntry : quantitiesByProductCode.entrySet()) {
            dStockQuantityLines.add(DStockQuantityLine.builder()
                    .code(quantityEntry.getKey())
                    .quantity(quantityEntry.getValue())
                    .build());
        }
        return dStockQuantityLines;
    }

    /**
     * Índice código → fila de inventario (si hay duplicados en la respuesta, se conserva la primera).
     */
    private static Map<String, DStockResponse> indexStockByProductCode(List<DStockResponse> dStockResponses) {
        Map<String, DStockResponse> dStockResponseByProductCode = new HashMap<>();
        if (dStockResponses == null) {
            return dStockResponseByProductCode;
        }
        for (DStockResponse dStockResponse : dStockResponses) {
            if (dStockResponse.getCode() != null && !dStockResponseByProductCode.containsKey(dStockResponse.getCode())) {
                dStockResponseByProductCode.put(dStockResponse.getCode(), dStockResponse);
            }
        }
        return dStockResponseByProductCode;
    }

    private void postWithoutResponseBody(String path, List<DStockQuantityLine> dStockQuantityLines, String operationLabel) {
        try {
            stockRestClient.post()
                    .uri(path)
                    .bodyValue(dStockQuantityLines)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block(Duration.ofMillis(requestTimeOutMilliSeconds));
        } catch (WebClientResponseException webClientException) {
            String errorResponseBody = webClientException.getResponseBodyAsString();
            log.error("ms-common-stock POST {} -> HTTP {} body={}",
            operationLabel, webClientException.getStatusCode(), errorResponseBody);
            if (webClientException.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    errorResponseBody != null && !errorResponseBody.isBlank()
                        ? errorResponseBody
                        : "Conflicto de inventario al aplicar movimiento.",
                    webClientException);
            }
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "El servicio de inventario respondió con error: " + webClientException.getStatusCode(),
                webClientException);
        } catch (Exception exception) {
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            log.error("Fallo de comunicación con ms-common-stock ({}): {}", operationLabel, cause.getMessage(), exception);
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo actualizar inventario. Verifique que ms-common-stock esté disponible.", exception);
        }
    }

    private List<DStockResponse> fetchStockRows(List<String> productCodes) {
        log.debug("GET {} codes={}", CODES_PATH, productCodes);
        try {
            URI requestUri = UriComponentsBuilder.fromPath(CODES_PATH)
                    .queryParam("codes", productCodes.toArray())
                    .build()
                    .toUri();
            List<DStockResponse> dStockResponses = stockRestClient.get()
                    .uri(requestUri)
                    .retrieve()
                    .bodyToFlux(DStockResponse.class)
                    .collectList()
                    .block(Duration.ofMillis(requestTimeOutMilliSeconds));
            return dStockResponses != null ? dStockResponses : List.of();
        } catch (WebClientResponseException webClientException) {
            String errorResponseBody = webClientException.getResponseBodyAsString();
            log.error("ms-common-stock HTTP {} al consultar códigos {}: {}",
            webClientException.getStatusCode(), productCodes, errorResponseBody);
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "El servicio de inventario respondió con error: " + webClientException.getStatusCode(),
                webClientException);
        } catch (Exception exception) {
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            log.error("Fallo de comunicación con ms-common-stock (códigos {}): {}",
            productCodes, cause.getMessage(), exception);
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo consultar inventario. Verifique que ms-common-stock esté disponible.", exception);
        }
    }

}
