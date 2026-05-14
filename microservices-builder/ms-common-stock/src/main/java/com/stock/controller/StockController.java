package com.stock.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.stock.dtos.DStockEntryResponse;
import com.stock.dtos.DStockQuantityLine;
import com.stock.dtos.DStockResponse;
import com.stock.dtos.DStockUpsertRequest;
import com.stock.service.StockService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/stock")
@Tag(
        name = "Inventario",
        description = """
                Consulta por códigos (`/query/codes`), movimientos (`/deduct`, `/restore`) y CRUD de fila en **`/row`**.

                Los **400** / **409** documentados provienen de `StockService` (`ResponseStatusException`). \
                Un **400** adicional puede producirlo Spring si el JSON del cuerpo no es válido.""")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping("/list")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Listar filas de stock (paginado)",
            description = "Paginación estándar Spring (`page`, `size`, `sort`); sin filtros por fecha.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Página de `DStockEntryResponse`.",
                    content = @Content(schema = @Schema(implementation = DStockEntryResponse.class)))
    })
    public Page<DStockEntryResponse> listRows(
            @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
        return stockService.findAllEntries(pageable);
    }

    @GetMapping("/query/codes")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Consultar stocks por códigos",
            description = """
                    Query repetido **`codes`**. Solo devuelve filas existentes; códigos sin registro no aparecen \
                    (lista puede ser más corta que los códigos pedidos). Sin **404**.""")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Lista de `DStockResponse` (puede vacía).",
                    content = @Content(schema = @Schema(implementation = DStockResponse.class)))
    })
    public List<DStockResponse> getStocksByCodes(@RequestParam("codes") List<String> productCodes) {
        return stockService.getStocksByCodes(productCodes);
    }

    @PostMapping("/deduct")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Descontar stock",
            description = """
                    Descuento atómico por código. Ver `mergeQuantities` y `decreaseQuantity` en `StockService`.""")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Todos los descuentos aplicados."),
            @ApiResponse(
                    responseCode = "409",
                    description = """
                            No se actualizó ninguna fila para un código (stock insuficiente o código inexistente)."""),
            @ApiResponse(
                    responseCode = "400",
                    description = """
                            Líneas sin código o cantidad ≤ 0 (`mergeQuantities`), o JSON de petición ilegible (Spring).""")
    })
    public void deduct(@RequestBody List<DStockQuantityLine> lines) {
        stockService.deduct(lines);
    }

    @PostMapping("/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Restaurar stock",
            description = """
                    Suma cantidades por código. Lista vacía → **204** sin cambios (mapa vacío en servicio).""")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Restauración aplicada o sin trabajo."),
            @ApiResponse(
                    responseCode = "400",
                    description = """
                            Líneas inválidas (`mergeQuantities`) o JSON ilegible (Spring). No hay **409** en este método.""")
    })
    public void restore(@RequestBody List<DStockQuantityLine> lines) {
        stockService.restore(lines);
    }

    @GetMapping("/row/{stockId}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Obtener fila por stockId")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Encontrada.",
                    content = @Content(schema = @Schema(implementation = DStockEntryResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe `stockId`.")
    })
    public DStockEntryResponse getRow(@PathVariable Long stockId) {
        return stockService.findEntryById(stockId);
    }

    @PostMapping("/row")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear fila de stock", description = "Código único en BD (`existsByCode`).")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Creada.",
                    content = @Content(schema = @Schema(implementation = DStockEntryResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Código obligatorio, cantidad obligatoria al crear, o cantidad negativa."),
            @ApiResponse(responseCode = "409", description = "Ya existe una fila con ese código."),
            @ApiResponse(
                    responseCode = "400",
                    description = """
                            Validación: código vacío, cantidad obligatoria al crear, cantidad negativa; \
                            o JSON ilegible (Spring MVC).""")
    })
    public DStockEntryResponse createRow(@RequestBody DStockUpsertRequest request) {
        return stockService.createEntry(request);
    }

    @PutMapping("/row/{stockId}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Actualizar fila", description = "Merge parcial; `request` null devuelve estado actual.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Actualizada.",
                    content = @Content(schema = @Schema(implementation = DStockEntryResponse.class))),
            @ApiResponse(responseCode = "404", description = "Fila no existe."),
            @ApiResponse(responseCode = "409", description = "Otro registro ya usa el nuevo código."),
            @ApiResponse(responseCode = "400", description = "Cantidad negativa o JSON ilegible (Spring).")
    })
    public DStockEntryResponse updateRow(
            @PathVariable Long stockId, @RequestBody DStockUpsertRequest request) {
        return stockService.updateEntry(stockId, request);
    }

    @DeleteMapping("/row/{stockId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar fila por stockId")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Eliminada."),
            @ApiResponse(responseCode = "404", description = "Id inexistente.")
    })
    public void deleteRow(@PathVariable Long stockId) {
        stockService.deleteEntry(stockId);
    }

}
