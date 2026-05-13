# Microservicios intermedio

Monorepo Maven (`microservices-builder`) con microservicios Spring Boot que colaboran entre sí. Este documento resume el **layout del proyecto** y describe en detalle el **flujo de creación de órdenes** en **ms-common-orders** y su integración con inventario (**ms-common-stock**).

---

## Estructura del repositorio

```
microservicios-intermedio/
└── microservices-builder/   # POM padre (agrupa todos los módulos)
    ├── pom.xml              # BOM Spring Cloud + lista de módulos
    ├── ms-discovery-server/ # Eureka (Netflix), descubrimiento de servicios
    ├── ms-api-gateway/      # Spring Cloud Gateway (rutas lb:// + proxy Eureka)
    ├── ms-common-products/  # Catálogo (MongoDB)
    ├── ms-common-stock/     # Inventario (PostgreSQL + Flyway)
    └── ms-common-orders/    # Órdenes (PostgreSQL); llama a stock por HTTP
```

---

## Puertos por defecto (desarrollo local)

| Servicio            | Puerto   | Rol principal |
|---------------------|----------|---------------|
| ms-api-gateway      | **9090** | Punto de entrada: `/api/product/**`, `/api/order/**`, `/api/stock/**`, y rutas `/eureka/...` hacia el panel en 8083 |
| ms-common-products  | **8080** | API productos (`/api/product/...`) |
| ms-common-orders    | **8081** | API órdenes (`/api/order/...`) |
| ms-common-stock     | **8082** | Inventario (`/api/stock/...`) |
| ms-discovery-server | **8083** | Eureka: registro + panel web (**HTTP Basic** por defecto `eureka` / `password`; ver `ms-discovery-server` → `application.yaml`) |

Los micros y el gateway registran en Eureka con `defaultZone: http://eureka:password@localhost:8083/eureka` (mismas credenciales).

**Nota:** si cambias usuario o contraseña de Eureka, actualízalos en **todos** los `defaultZone` y en las rutas `uri:` del gateway hacia el discovery server.

No hay choque de puertos: **8080–8083** micros y descubrimiento; **9090** solo el gateway.

**Orders → stock** sigue yendo en directo a stock según `stock.service.base-url` (por defecto `http://localhost:8082` en `ms-common-orders`). El gateway **no** cambia eso; solo afecta a quien llame a la API pública por `http://localhost:9090/...`.

---

## API Gateway (`ms-api-gateway`, puerto **9090**)

El **API Gateway** es el **único punto de entrada HTTP** que quieres dar a clientes externos (Postman): misma familia de rutas que ya tienen los micros (`/api/product/...`, `/api/order/...`, `/api/stock/...`), pero el host y el puerto son siempre **`http://localhost:9090`**.

### Qué logra (objetivo)

- **Una base URL** para “enmascarar” el reparto: tú llamas `http://localhost:9090/api/order`, el gateway elige una instancia de **ms-common-orders** registrada en Eureka (`lb://ms-common-orders`) y reenvía la petición.
- **Descubrimiento + balanceo** hacia réplicas del mismo servicio, sin que el cliente conozca 8081, 557xx, etc.
- **Proxy opcional al panel Eureka** bajo el mismo origen: `http://localhost:9090/eureka/web` (y `/eureka/**` para recursos estáticos del servidor en **8083**).

No sustituye a Eureka ni a los micros: deben estar levantados y registrados; el gateway solo **enruta**.

### Ejemplos (desarrollo local)

| Acción | URL vía gateway |
|--------|------------------|
| Crear orden | `POST http://localhost:9090/api/order` |
| Listar órdenes | `GET http://localhost:9090/api/order` |
| Stock (p. ej. códigos) | `GET http://localhost:9090/api/stock/codes?...` |
| Productos | `GET` / `POST` `http://localhost:9090/api/product` … |
| Panel Eureka | `http://localhost:9090/eureka/web` |

Llamar **directo** a `http://localhost:8081/...` sigue siendo válido para depurar una sola instancia; para el comportamiento “curso / producción simplificada”, usa **9090**.

Configuración: `microservices-builder/ms-api-gateway/src/main/resources/application.yaml` (`server.port`, rutas `spring.cloud.gateway.server.webmvc.routes`).

---

## Flujo de creación de una orden (`POST /api/order`)

Todo ocurre en **ms-common-orders**, salvo las llamadas HTTP a **ms-common-stock**. La entrada es un `DOrderRequest` con lista `orderLineItemsList` (código de producto + cantidad por línea).

### Visión rápida (orden temporal)

1. **HTTP entra** → `OrderController.createOrder` registra en log cuántas líneas trae el cuerpo.
2. **Dominio en memoria** → `OrderService.createOrder` mapea el DTO a entidad y asigna un `orderNumber` (UUID). Aún **no** hay fila en base de datos de órdenes.
3. **Agregación por SKU** → Se suman cantidades por código (varias líneas pueden repetir el mismo producto). Si no queda ningún código válido → **400 Bad Request**.
4. **Solo lectura en inventario** → `StockWebClient.evaluateEligibility` llama a stock con **GET** `/api/stock/codes?codes=...` y compara, por cada código, la **demanda total** pedida frente a la **cantidad en almacén** devuelta. No se descuenta nada todavía.
5. **Filtrado de líneas** → Solo se conservan líneas cuyo código está en el conjunto “elegible”. Los motivos de exclusión (sin registro, stock insuficiente, etc.) se acumulan como textos en `StockEligibility.skippedLineReasons`.
6. **Si ninguna línea pasó** → **409 Conflict** con el detalle unido de exclusiones (error de negocio local; no viene del POST deduct).
7. **Inventario parcial** → Si al menos una línea es elegible pero hubo exclusiones, se sigue adelante; las exclusiones pueden devolverse al cliente en la respuesta (ver más abajo).
8. **Escritura en stock** → **POST** `/api/stock/deduct` con las cantidades **solo de las líneas aceptadas** (descuento atómico por código en la BD de stock).
9. **Persistencia de la orden** → Si el `save` en PostgreSQL de órdenes falla **después** de un deduct exitoso, se intenta **POST** `/api/stock/restore` con las mismas cantidades para **compensar** el descuento (patrón “deduct + restore” si la orden no se guardó).
10. **Respuesta** → **201 Created** con `DOrderResponse`: líneas guardadas y, si aplica, `inventoryExclusions` con los motivos de las líneas omitidas.

### Diagrama lógico (texto)

```
Cliente
   │ POST /api/order
   ▼
OrderController ──────────────────────────────────────────────┐
   │                                                          │
   ▼                                                          │
OrderService                                                  │
   │ map + UUID orderNumber                                   │
   │ aggregate quantities por código                          │
   │                                                          │
   │    ┌───────────────────────────────────────────┐         │
   │    │ StockWebClient.evaluateEligibility        │         │
   │    │   GET stock → /api/stock/codes            │         │
   │    │   (solo lectura; compara demanda vs stock)│         │
   │    └───────────────────────────────────────────┘         │
   │                                                          │
   │ filtrar líneas elegibles                                 │
   │ si vacío → 409                                           │
   │                                                          │
   │    ┌──────────────────────────────────────────┐          │
   │    │ StockWebClient.deductQuantities          │          │
   │    │   POST stock → /api/stock/deduct         │          │
   │    └──────────────────────────────────────────┘          │
   │                                                          │
   │ save(order) en BD órdenes                                │
   │ si falla save tras deduct OK → restore (POST restore)    │
   │                                                          │
   └──────────────────────────────────────────────────────────┘
```

---

## Integración HTTP orders → stock

Definida en `StockWebClient` y propiedades `stock.service.paths.*`:

| Paso              | Método HTTP | Ruta en stock        | Efecto                                           |
|-------------------|-------------|----------------------|--------------------------------------------------|
| Elegibilidad      | GET         | `/api/stock/codes`   | Consulta cantidades; **no modifica** inventario. |
| Descontar         | POST        | `/api/stock/deduct`  | Descuenta en BD de forma acordada con el micro de stock (409 si no puede). |
| Compensar         | POST        | `/api/stock/restore` | Devuelve unidades si hubo deduct y falló el guardado de la orden. |
| (rollback lógico) |

Los fallos de red o HTTP anómalos del stock se envuelven en `UpstreamDependencyException` y el `OrdersExceptionHandler` devuelve un JSON con `service: ms-common-stock`, `status`, `message`, etc. (**502** / **503** / **409** según el caso).

---

## Cuándo la orden “sale bien”

### Caso A — Todo el inventario alcanza

- Todas las líneas con código válido tienen stock suficiente según el GET.
- Se hace deduct de todas esas cantidades agregadas por código.
- Se persiste la orden con todas esas líneas.
- Respuesta **201** sin `inventoryExclusions` (o lista vacía / null según serialización).

### Caso B — Inventario parcial (negocio permitido)

- Algunas líneas no cumplen (sin SKU en stock o cantidad insuficiente); otras sí.
- Solo las líneas **elegibles** se incluyen en el deduct y en el `save`.
- Respuesta **201** con **`inventoryExclusions`**: lista de strings explicando qué se omitió (por ejemplo “Stock insuficiente para SKU-X…”).

En logs verás advertencias del estilo “inventario parcial” en `OrderService`.

---

## Cuándo falla (y qué esperar)

| Situación | Código HTTP | Origen |
|-----------|-------------|--------|
| Request sin líneas válidas (códigos vacíos / cantidades ≤ 0 tras agregar) | **400** | `OrderService` / validación de líneas |
| Ninguna línea pasa la elegibilidad (todo el pedido incumple stock o sin registro) | **409** | `OrderService` (mensaje con exclusiones unidas) |
| Stock responde **409** en **deduct** (condición de carrera o estado ya no permite descontar) | **409** | Propagado como error de dependencia (`UpstreamDependencyException`) |
| Stock devuelve error HTTP no esperado al consultar o mover inventario | **502** | Cliente HTTP → handler |
| Stock no responde (timeout, caído, red) | **503** | Cliente HTTP → handler |
| Falló `save` de la orden **después** de deduct: se intenta **restore**; si restore también falla, hay **error log crítico** (inventario puede quedar inconsistente hasta intervención manual). | Depende de la excepción relanzada | Orden no persistida; cliente ve error del save |

Los errores **puremente locales** (400, 409 sin líneas) siguen el formato estándar de Spring sin campo `service`. Los errores por dependencia usan el cuerpo documentado en OpenAPI (`OrdersDependencyError` / `DOrdersDependencyErrorBody`).

---

## Logs recomendados para seguir una orden

En **orders** y **StockWebClient** hay `log.info` con prefijos coherentes:

- `POST /api/order: inicio createOrder` — entrada al controlador.
- `createOrder: ...` — fases numeradas en `OrderService` (preparación, elegibilidad, deduct, save, compensación).
- `[stock] ...` — llamadas y resultados en `StockWebClient` (GET códigos, POST deduct/restore).

Filtra por `orderNumber` o por `[stock]` para aislar el flujo en consola.

---

## Documentación interactiva (Swagger)

Con cada micro levantado:

- Órdenes: `http://localhost:8081/swagger-ui.html`
- Stock: `http://localhost:8082/swagger-ui.html`
- Productos: `http://localhost:8080/swagger-ui.html`

El **API Gateway (9090)** no expone por defecto una Swagger unificada; la documentación interactiva sigue en cada micro en su puerto directo. Las llamadas REST de negocio sí pueden ir por `http://localhost:9090/api/...` (ver sección API Gateway arriba).

La descripción de códigos de respuesta del **POST crear orden** está anotada en `OrderController` (OpenAPI).

---

## Requisitos típicos para probar el flujo completo

1. PostgreSQL con bases `ms_common_orders` y `ms_common_stock` (según `application.yaml` de cada uno).
2. **ms-common-stock** en ejecución antes de crear órdenes que descuenten inventario.
3. Datos de stock suficientes para los SKU que envíes en el POST (o esperar 409 / exclusiones según reglas anteriores).

---

## Más detalle en código

- Flujo y comentarios paso a paso: `ms-common-orders/.../OrderService.java` → método `createOrder`.
- Cliente HTTP y manejo de errores: `StockWebClient.java`.
- Respuesta con exclusiones: `DOrderResponse.inventoryExclusions`.

Si incorporas **Eureka**, recuerda que el descubrimiento no sustituye por sí solo las URLs del `WebClient` de stock: `stock.service.base-url` sigue siendo la configuración activa salvo que migres a resolución por nombre de servicio y balanceo.
