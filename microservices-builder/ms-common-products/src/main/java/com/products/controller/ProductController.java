package com.products.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
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

import com.products.dtos.DProductRequest;
import com.products.dtos.DProductResponse;
import com.products.service.ProductService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/product")
@Tag(
        name = "Productos",
        description = """
                CRUD sobre MongoDB (`product`). El **productId** lo genera la base al crear.

                **Respuestas**
                — **POST** `/create`: **201** sin cuerpo.
                — **GET** `/list`: **200** con `Page` de Spring (`content` = `ProductResponse`).
                — **GET** `/by-id/{productId}`: **200** + `ProductResponse`, o **404** si no existe.
                — **PUT** `/by-id/{productId}`: **200** sin cuerpo, o **404** si no existe.
                — **DELETE** `/by-id/{productId}`: **204** sin cuerpo, o **404** si no existe.

                En **POST**/**PUT**, un **400** solo aparece si Spring no puede leer el JSON del cuerpo \
                (p. ej. sintaxis inválida); este servicio no aplica validaciones Bean Validation propias que devuelvan 400.""")
public class ProductController {

    @Autowired
    private ProductService productService;

    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Crear producto",
            description = """
                    Persiste un documento. No envíe **productId**.

                    Respuesta **201** sin cuerpo JSON.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Creado (sin cuerpo)."),
            @ApiResponse(
                    responseCode = "400",
                    description = "Cuerpo no deserializable como JSON (error de Spring MVC antes de llegar al servicio).")
    })
    public void create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Campos del producto.",
                    required = true,
                    content = @Content(schema = @Schema(implementation = DProductRequest.class)))
            @RequestBody DProductRequest productRequest) {
        productService.createProduct(productRequest);
    }

    @GetMapping("/list")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Listar productos (paginado)",
            description = "Query: **page** (base 0), **size**. Respuesta: objeto `Page` estándar de Spring Data.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Página de productos.",
                    content = @Content(schema = @Schema(implementation = DProductResponse.class)))
    })
    public Page<DProductResponse> productResponseList(
            @Parameter(description = "Índice de página (primera = 0).", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página.", example = "10")
            @RequestParam(defaultValue = "10") int size) {
        return productService.getAllProducts(page, size);
    }

    @GetMapping("/by-id/{productId}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Obtener producto por id", description = "Devuelve el documento o **404** si el id no existe.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Encontrado.",
                    content = @Content(schema = @Schema(implementation = DProductResponse.class))),
            @ApiResponse(responseCode = "404", description = "`ResponseStatusException`: producto no encontrado.")
    })
    public DProductResponse getById(
            @Parameter(description = "_id en MongoDB.", example = "674a1b2c3d4e5f6789012345", required = true)
            @PathVariable String productId) {
        return productService.getProductById(productId);
    }

    @PutMapping("/by-id/{productId}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Actualizar producto",
            description = """
                    Fusiona campos según el mapper. **200** sin cuerpo si existe; **404** si el id no existe.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Actualizado (sin cuerpo)."),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado."),
            @ApiResponse(
                    responseCode = "400",
                    description = "Cuerpo no deserializable como JSON (Spring MVC).")
    })
    public void update(
            @Parameter(description = "Id del documento.", example = "674a1b2c3d4e5f6789012345", required = true)
            @PathVariable String productId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Campos a fusionar.",
                    required = true,
                    content = @Content(schema = @Schema(implementation = DProductRequest.class)))
            @RequestBody DProductRequest productRequest) {
        productService.updateProduct(productId, productRequest);
    }

    @DeleteMapping("/by-id/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar producto", description = "**204** si existía y se borró; **404** si el id no existe.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Eliminado (sin cuerpo)."),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado.")
    })
    public void delete(
            @Parameter(description = "Id del documento.", example = "674a1b2c3d4e5f6789012345", required = true)
            @PathVariable String productId) {
        productService.deleteProduct(productId);
    }

}
