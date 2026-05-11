package com.orders.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.orders.dtos.DOrderRequest;
import com.orders.dtos.DOrderResponse;
import com.orders.service.OrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * API HTTP de órdenes (puerto configurado en {@code server.port}, por defecto 8081).
 * Documentación interactiva: {@code /swagger-ui.html} (SpringDoc).
 */
@RestController
@RequestMapping("/api/order")
@Tag(name = "Órdenes", description = """
        Creación de órdenes con líneas de producto y consulta paginada. \
        La creación consulta **ms-common-stock** (existencia, cantidad disponible, débito y restauración en fallo).""")
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     * Crea una orden validando inventario por código y cantidad agregada.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Crear orden",
            description = """
                    Recibe una lista de líneas (`orderLineItemsList`). Se validan códigos y cantidades contra \
                    **ms-common-stock**. Las líneas sin stock suficiente o sin registro en inventario se omiten; \
                    si al menos una línea es válida, se persiste la orden y se descuenta stock. \
                    Si hubo omisiones parciales, la respuesta incluye `inventoryExclusions` con los motivos. \
                    Si ninguna línea es válida, responde **409 Conflict** con el detalle. \
                    Errores de comunicación con inventario: **502** o **503**.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Orden creada (puede incluir exclusiones de inventario en el cuerpo).",
                    content = @Content(schema = @Schema(implementation = DOrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Cuerpo inválido: sin códigos, cantidades ≤ 0 o líneas inconsistentes."),
            @ApiResponse(responseCode = "409", description = "Ninguna línea cumple inventario (detalle en cuerpo RFC7807)."),
            @ApiResponse(responseCode = "502", description = "ms-common-stock respondió con error HTTP."),
            @ApiResponse(responseCode = "503", description = "No se pudo contactar ms-common-stock (red / caído).")
    })
    public DOrderResponse createOrder(
            @RequestBody(
                    description = "Orden con líneas: cada ítem requiere `code`, `quantity` > 0 y datos de precio/descripción.",
                    required = true,
                    content = @Content(schema = @Schema(implementation = DOrderRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody DOrderRequest dOrderRequest) {
        return orderService.createOrder(dOrderRequest);
    }

    /**
     * Lista órdenes persistidas con paginación estándar de Spring Data.
     */
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Listar órdenes (paginado)",
            description = """
                    Devuelve una página de órdenes con sus líneas. Parámetros `page` y `size` siguen la convención \
                    Spring Data (página base 0).""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de órdenes (Spring Data: content, totalElements, totalPages, etc.).",
                    content = @Content(schema = @Schema(implementation = DOrderResponse.class)))
    })
    public Page<DOrderResponse> orderResponseList(
            @Parameter(description = "Índice de página (0 = primera).", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página (máximo de elementos por página).", example = "10")
            @RequestParam(defaultValue = "10") int size) {
        return orderService.getAllOrders(page, size);
    }

}
