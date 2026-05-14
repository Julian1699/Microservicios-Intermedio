package com.orders.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.orders.dtos.DOrdersDependencyErrorBody;
import com.orders.dtos.DOrderRequest;
import com.orders.dtos.DOrderResponse;
import com.orders.service.OrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/order")
@Tag(
        name = "Órdenes",
        description = """
                Creación con validación y débito en **ms-common-stock** (`StockWebClient`). \
                Listado paginado. Consulta por id o por `orderNumber`. Eliminación con **restore** de cantidades en stock.

                Códigos de error alineados con `OrderService` y propagación desde `StockWebClient` \
                (consultar cada operación).""")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Crear orden",
            description = """
                    Valida líneas (código + cantidad > 0), consulta stock por HTTP, descuenta en ms-common-stock y persiste.

                    Éxito: **201** con cuerpo `DOrderResponse`.""")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Orden creada (puede incluir `inventoryExclusions` si hubo líneas omitidas).",
                    content = @Content(schema = @Schema(implementation = DOrderResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = """
                            Reglas de negocio en `OrderService` (líneas sin códigos válidos, cantidad ≤ 0, etc.) \
                            **o** cuerpo JSON ilegible antes del servicio (error estándar Spring, sin `service`)."""),
            @ApiResponse(
                    responseCode = "409",
                    description = """
                            **Caso A:** ninguna línea cumple inventario — error estándar Spring (sin `service`). \
                            **Caso B:** conflicto devuelto por ms-common-stock al descontar — cuerpo `OrdersDependencyError` \
                            con `service`: ms-common-stock."""),
            @ApiResponse(
                    responseCode = "502",
                    description = "ms-common-stock respondió con error HTTP al consultar códigos o al descontar.",
                    content = @Content(schema = @Schema(implementation = DOrdersDependencyErrorBody.class))),
            @ApiResponse(
                    responseCode = "503",
                    description = "Sin respuesta HTTP válida desde ms-common-stock (caído / red).",
                    content = @Content(schema = @Schema(implementation = DOrdersDependencyErrorBody.class)))
    })
    public DOrderResponse createOrder(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Lista `orderLineItemsList` con líneas que tengan `code` y `quantity` > 0 donde aplique.",
                    required = true,
                    content = @Content(schema = @Schema(implementation = DOrderRequest.class)))
            @RequestBody DOrderRequest dOrderRequest) {
        int lineCount = dOrderRequest != null && dOrderRequest.getOrderLineItemsList() != null
        ? dOrderRequest.getOrderLineItemsList().size() : 0;
        log.info("POST /api/order/create: inicio createOrder ({} líneas en el cuerpo)", lineCount);
        return orderService.createOrder(dOrderRequest);
    }

    @GetMapping("/list")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Listar órdenes (paginado)",
            description = "Solo lectura local; no llama a ms-common-stock.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Página Spring Data (`Page`).",
                    content = @Content(schema = @Schema(implementation = DOrderResponse.class)))
    })
    public Page<DOrderResponse> orderResponseList(
            @Parameter(description = "Página base 0.", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página.", example = "10") @RequestParam(defaultValue = "10") int size) {
        return orderService.getAllOrders(page, size);
    }

    @GetMapping("/by-number/{orderNumber}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Obtener orden por orderNumber", description = "Generado al crear la orden (UUID).")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Encontrada.",
                    content = @Content(schema = @Schema(implementation = DOrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "`orderNumber` vacío o solo espacios."),
            @ApiResponse(responseCode = "404", description = "No existe orden con ese número.")
    })
    public DOrderResponse getOrderByOrderNumber(
            @Parameter(description = "Valor de `orderNumber` devuelto al crear.", required = true)
            @PathVariable String orderNumber) {
        return orderService.getOrderByOrderNumber(orderNumber);
    }

    @GetMapping("/by-id/{orderId}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Obtener orden por id numérico", description = "Clave `orderId` en base de datos.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Encontrada.",
                    content = @Content(schema = @Schema(implementation = DOrderResponse.class))),
            @ApiResponse(responseCode = "404", description = "Orden no encontrada.")
    })
    public DOrderResponse getOrderById(
            @Parameter(description = "PK de la tabla orders.", example = "1", required = true)
            @PathVariable Long orderId) {
        return orderService.getOrderById(orderId);
    }

    @DeleteMapping("/by-id/{orderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Eliminar orden",
            description = """
                    Borra la orden y llama a **restore** en ms-common-stock con las cantidades de las líneas guardadas.

                    Si las líneas en BD fueran inconsistentes, `aggregateRequestedQuantity` podría lanzar **400** (caso anómalo).""")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Eliminada; stock restaurado si había líneas con cantidades."),
            @ApiResponse(responseCode = "404", description = "Orden no encontrada."),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflicto al restaurar en ms-common-stock.",
                    content = @Content(schema = @Schema(implementation = DOrdersDependencyErrorBody.class))),
            @ApiResponse(
                    responseCode = "502",
                    description = "Error HTTP de ms-common-stock al restaurar.",
                    content = @Content(schema = @Schema(implementation = DOrdersDependencyErrorBody.class))),
            @ApiResponse(
                    responseCode = "503",
                    description = "Sin conexión a ms-common-stock al restaurar.",
                    content = @Content(schema = @Schema(implementation = DOrdersDependencyErrorBody.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Poco habitual: líneas almacenadas incoherentes al sumar cantidades para restore.")
    })
    public void deleteOrder(
            @Parameter(description = "PK de la orden.", example = "1", required = true) @PathVariable Long orderId) {
        orderService.deleteOrder(orderId);
    }

}
