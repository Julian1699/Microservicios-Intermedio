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

import com.orders.exception.UpstreamDependencyException;
import com.orders.dtos.DStockQuantityLine;
import com.orders.dtos.DStockResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Cliente HTTP (WebClient) hacia <strong>ms-common-stock</strong>. Es el único punto en órdenes que habla con inventario.
 * <p>
 * Flujo típico al crear una orden:
 * <ol>
 *   <li>{@link #evaluateEligibility(Map)} — GET a {@code /api/stock/codes} para saber qué códigos tienen cantidad suficiente.</li>
 *   <li>{@link #deductQuantities(Map)} — POST a {@code /api/stock/deduct} para descontar lo ya validado.</li>
 *   <li>Si falla el guardado de la orden, el servicio puede llamar {@link #restoreQuantities(Map)} — POST a {@code /api/stock/restore}.</li>
 * </ol>
 * <p>
 * <strong>Configuración</strong>
 * <ul>
 *   <li>Rutas relativas al base URL: {@code stock.service.paths.*} en {@code application.yaml} (valores por defecto en {@code @Value}).</li>
 *   <li>Timeout de red: solo en {@link WebClientConfig} ({@code HttpClient.responseTimeout}) sobre el bean {@code stockRestClient}.
 *       Aquí se usa {@code .block()} sin segundo timeout para no duplicar.</li>
 * </ul>
 * <p>
 * <strong>Errores hacia el caller</strong>: fallos de comunicación o HTTP anómalo del stock se propagan como
 * {@link UpstreamDependencyException} (luego el {@code OrdersExceptionHandler} arma el JSON con {@code dependencyService}).
 */
@Slf4j
@Component
public class StockWebClient {

    /** Nombre lógico del microservicio remoto (para respuestas de error y logs). */
    private static final String STOCK_SERVICE_NAME = "ms-common-stock";

    /**
     * Resultado de {@link #evaluateEligibility(Map)}: qué códigos pueden cubrirse con el stock actual y por qué se excluyen otros.
     *
     * @param eligibleCodes        códigos de producto cuya cantidad agregada solicitada cabe en inventario
     * @param skippedLineReasons   textos explicativos por código sin fila en stock, o con cantidad insuficiente
     */
    public record StockEligibility(Set<String> eligibleCodes, List<String> skippedLineReasons) {}

    private final WebClient stockRestClient;

    private final String stockCodesPath;

    private final String stockDeductPath;

    private final String stockRestorePath;

    /**
     * Inyecta el WebClient ya configurado (base URL + timeout) y las rutas de stock leídas de propiedades.
     */
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
     * Pide a ms-common-stock las filas de inventario para los códigos pedidos y decide, por cada código,
     * si la <strong>demanda total</strong> (suma de líneas de la orden con ese código) cabe en la cantidad devuelta.
     * <p>
     * No modifica stock; solo lee. Si un código no viene en la respuesta del GET, se considera “sin registro”.
     *
     * @param requestedQuantityByProductCode mapa código → unidades totales pedidas (ya agregadas por el llamador)
     * @return códigos elegibles y lista de motivos para los no elegibles (vacío si mapa de entrada vacío o null)
     * @throws UpstreamDependencyException {@code 502} si stock respondió error HTTP; {@code 503} si no hubo conexión/timeout
     */
    public StockEligibility evaluateEligibility(Map<String, Integer> requestedQuantityByProductCode) {
        if (requestedQuantityByProductCode == null || requestedQuantityByProductCode.isEmpty()) {
            return new StockEligibility(Set.of(), List.of());
        }
        List<String> productCodes = new ArrayList<>(requestedQuantityByProductCode.keySet());
        log.info("[stock] evaluateEligibility: GET {} — {} códigos distintos (demanda ya agregada por código)",
        stockCodesPath, productCodes.size());
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
        log.info("[stock] evaluateEligibility: fin — elegibles={} exclusiones={}",
        eligibleProductCodes.size(), skippedInventoryReasons.size());
        return new StockEligibility(eligibleProductCodes, skippedInventoryReasons);
    }

    /**
     * Envía a ms-common-stock el descuento atómico por código (POST {@code deduct}). El stock aplica updates en BD.
     * <p>
     * No hace nada si el mapa es null o vacío.
     *
     * @param quantitiesByProductCode código → cantidad a restar (positiva)
     * @throws UpstreamDependencyException {@code 409} si stock devolvió conflicto; {@code 502} otro error HTTP;
     *                                       {@code 503} red / servicio caído / timeout
     */
    public void deductQuantities(Map<String, Integer> quantitiesByProductCode) {
        if (quantitiesByProductCode == null || quantitiesByProductCode.isEmpty()) {
            return;
        }
        log.info("[stock] deductQuantities: POST {} — {} códigos a descontar", stockDeductPath, quantitiesByProductCode.size());
        postWithoutResponseBody(stockDeductPath, dStockQuantityLinesFromMap(quantitiesByProductCode), "deduct");
    }

    /**
     * Devuelve unidades al inventario (POST {@code restore}), p. ej. tras descontar y fallar el guardado de la orden en BD.
     * <p>
     * No hace nada si el mapa es null o vacío.
     *
     * @param quantitiesByProductCode código → cantidad a sumar de vuelta
     * @throws UpstreamDependencyException {@code 409} conflicto en stock; {@code 502} error HTTP; {@code 503} sin conexión
     */
    public void restoreQuantities(Map<String, Integer> quantitiesByProductCode) {
        if (quantitiesByProductCode == null || quantitiesByProductCode.isEmpty()) {
            return;
        }
        log.info("[stock] restoreQuantities: POST {} — {} códigos a devolver", stockRestorePath, quantitiesByProductCode.size());
        postWithoutResponseBody(stockRestorePath, dStockQuantityLinesFromMap(quantitiesByProductCode), "restore");
    }

    /**
     * Convierte el mapa interno de la orden (código → cantidad) al JSON que espera ms-common-stock: lista de
     * {@link DStockQuantityLine} con {@code code} y {@code quantity}.
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
     * Construye un mapa código → primera fila de respuesta, para búsquedas O(1) en {@link #evaluateEligibility(Map)}.
     * Si el mismo código viniera duplicado en la lista (no debería), solo se conserva la primera entrada.
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

    /**
     * POST genérico a una ruta de movimiento de stock ({@code deduct} o {@code restore}): cuerpo JSON = lista de líneas,
     * respuesta esperada vacía (204 / sin cuerpo). Centraliza el manejo de errores HTTP y de red.
     *
     * @param path                 ruta relativa al baseUrl del WebClient (p. ej. {@code /api/stock/deduct})
     * @param dStockQuantityLines  cuerpo serializable hacia ms-common-stock
     * @param operationLabel       etiqueta solo para logs (no se envía al servidor)
     * @throws UpstreamDependencyException ver cuerpo del método: 409, 502 o 503
     */
    private void postWithoutResponseBody(String path, List<DStockQuantityLine> dStockQuantityLines, String operationLabel) {
        try {
            stockRestClient.post()
                    .uri(path)
                    .bodyValue(dStockQuantityLines)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();
            log.info("[stock] POST {} ({}) OK — {} líneas en el cuerpo", path, operationLabel, dStockQuantityLines.size());
        } catch (WebClientResponseException webClientException) {
            String errorResponseBody = webClientException.getResponseBodyAsString();
            log.error("ms-common-stock POST {} -> HTTP {} body={}",
                    operationLabel, webClientException.getStatusCode(), errorResponseBody);
            if (webClientException.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                throw new UpstreamDependencyException(HttpStatus.CONFLICT,
                        errorResponseBody != null && !errorResponseBody.isBlank()
                                ? errorResponseBody
                                : "Conflicto de inventario al aplicar movimiento.",
                        STOCK_SERVICE_NAME,
                        webClientException);
            }
            throw new UpstreamDependencyException(
                    HttpStatus.BAD_GATEWAY,
                    "El servicio de inventario respondió con error: " + webClientException.getStatusCode(),
                    STOCK_SERVICE_NAME,
                    webClientException);
        } catch (Exception exception) {
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            log.error("Fallo de comunicación con ms-common-stock ({}): {}", operationLabel, cause.getMessage(), exception);
            throw new UpstreamDependencyException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo actualizar inventario. Verifique que ms-common-stock esté disponible.",
                    STOCK_SERVICE_NAME,
                    exception);
        }
    }

    /**
     * GET a {@code stockCodesPath} con query repetido {@code codes} para obtener cantidad e indicador {@code inStock}
     * por cada código que exista en la BD de stock. Códigos sin fila simplemente no aparecen en la lista devuelta.
     *
     * @param productCodes lista de códigos a consultar (puede repetirse el parámetro en la URL)
     * @return filas de inventario deserializadas; nunca null (lista vacía si la respuesta fue null)
     * @throws UpstreamDependencyException {@code 502} error HTTP del GET; {@code 503} sin conexión / timeout
     */
    private List<DStockResponse> fetchStockRows(List<String> productCodes) {
        log.info("[stock] fetchStockRows: GET {} — codes={}", stockCodesPath, productCodes);
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
            throw new UpstreamDependencyException(
                    HttpStatus.BAD_GATEWAY,
                    "El servicio de inventario respondió con error: " + webClientException.getStatusCode(),
                    STOCK_SERVICE_NAME,
                    webClientException);
        } catch (Exception exception) {
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            log.error("Fallo de comunicación con ms-common-stock (códigos {}): {}",
                    productCodes, cause.getMessage(), exception);
            throw new UpstreamDependencyException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo consultar inventario. Verifique que ms-common-stock esté disponible.",
                    STOCK_SERVICE_NAME,
                    exception);
        }
    }

}
