package com.orders.config;

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

import com.orders.dtos.DStockQuantityLine;
import com.orders.dtos.DStockResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Cliente de integración hacia ms-common-stock.
 * <ul>
 *   <li>Rutas HTTP: {@code stock.service.paths.*} en {@code application.yaml} (cada ruta con valor por defecto en
 *       el parámetro {@code @Value} del constructor).</li>
 *   <li>Timeout de red: solo en {@link WebClientConfig} vía {@code HttpClient.responseTimeout(...)} aplicado al bean
 *       {@code stockRestClient}. Aquí se usa {@link reactor.core.publisher.Mono#block()} sin duración propia para no
 *       duplicar la configuración; el transporte corta la espera según ese timeout.</li>
 * </ul>
 */
@Slf4j
@Component
public class StockWebClient {

    /**
     * Resultado de consultar inventario: códigos que pueden facturarse y motivos por los que otros quedan fuera.
     */
    public record StockEligibility(Set<String> eligibleCodes, List<String> skippedLineReasons) {}

    private final WebClient stockRestClient;

    private final String stockCodesPath;

    private final String stockDeductPath;

    private final String stockRestorePath;

    public StockWebClient(
            @Qualifier("stockRestClient") WebClient stockRestClient,
            @Value("${stock.service.paths.codes:/api/stock/codes}") String stockCodesPath,
            @Value("${stock.service.paths.deduct:/api/stock/deduct}") String stockDeductPath,
            @Value("${stock.service.paths.restore:/api/stock/restore}") String stockRestorePath) {
        this.stockRestClient = stockRestClient;
        this.stockCodesPath = stockCodesPath;
        this.stockDeductPath = stockDeductPath;
        this.stockRestorePath = stockRestorePath;
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
        postWithoutResponseBody(stockDeductPath, dStockQuantityLinesFromMap(quantitiesByProductCode), "deduct");
    }

    /**
     * Devuelve unidades al inventario si hubo que compensar tras un fallo al guardar la orden.
     */
    public void restoreQuantities(Map<String, Integer> quantitiesByProductCode) {
        if (quantitiesByProductCode == null || quantitiesByProductCode.isEmpty()) {
            return;
        }
        postWithoutResponseBody(stockRestorePath, dStockQuantityLinesFromMap(quantitiesByProductCode), "restore");
    }

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
                    .block();
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
        log.debug("GET {} codes={}", stockCodesPath, productCodes);
        try {
            List<DStockResponse> dStockResponses = stockRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(stockCodesPath)
                            .queryParam("codes", productCodes.toArray())
                            .build())
                    .retrieve()
                    .bodyToFlux(DStockResponse.class)
                    .collectList()
                    .block();
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
