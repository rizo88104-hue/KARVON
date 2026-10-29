# Karvon — project guidelines (TZ v2)

## What we are building
Karvon is a website for Uzbekistan where customers buy Taobao products through us.
A customer pastes a Taobao link (or searches in Uzbek/Russian), sees the final price in UZS,
pays with Payme or Click, and tracks the order until they receive it.

We are an intermediary. We have NO own warehouse, NO couriers, NO cargo batches.
- Our buyer in China purchases the item on Taobao.
- Delivery to Uzbekistan is done by Taobao official consolidation or a partner cargo company.
- Staff enter weight and tracking numbers manually.

## Roles
CUSTOMER, OPERATOR (support in Tashkent), BUYER (in China), ADMIN.

## Order statuses (in order)
AWAITING_PAYMENT -> PAID -> PURCHASING -> PURCHASED -> AT_WAREHOUSE
-> AWAITING_SHIPPING_PAYMENT (only if the real shipping cost is higher than prepaid)
-> IN_TRANSIT -> ARRIVED -> DELIVERED
Exception statuses: OUT_OF_STOCK, PRICE_CHANGED (customer decides: accept or refund), CANCELLED.
AWAITING_PAYMENT orders are cancelled automatically after 30 minutes.

## Pricing rules
goods      = (taobaoPriceCny + chinaShippingCny) * quantity * customerRate
serviceFee = max(goods * feePercent, minServiceFee)            // default 10%, min 15 000 UZS
shipping   = max(minWeightKg, weightKg) * quantity * (shippingCostPerKg + ourMarkupPerKg)
total      = goods + serviceFee + shipping
- All settings are edited by ADMIN and kept with history; each order stores the settings it was priced with.
- When the real weight is known: if shipping is higher, the customer pays the difference;
  if lower, the difference goes to the customer's wallet.
- Money is always BigDecimal. UZS is rounded to 100 so'm (HALF_UP). Never use double for money.

## Tech stack (do not change without asking)
- Java 21, Spring Boot 4.1.x, Maven (use ./mvnw)
- Spring Web (REST), Spring Security (JWT), Spring Data JPA, Bean Validation
- PostgreSQL 16 with Flyway migrations (never ddl-auto=update), Redis for cache and OTP codes
- springdoc-openapi (Swagger UI at /swagger-ui), MapStruct, Lombok (only @Getter/@Setter/@Builder/@RequiredArgsConstructor)
- JUnit 5, Mockito, AssertJ, Testcontainers
- Docker Compose for local Postgres and Redis
- Use Boot 4 starters (e.g. spring-boot-starter-flyway) and Boot 4 compatible library versions.
  If a library has no Boot 4 compatible version, ask before adding it.

## Architecture
- Modular monolith. Base package uz.karvon. Modules (one package each):
  auth, user, settings, pricing, catalog, translation, cart, order, payment, wallet,
  procurement, notification, admin, common.
- Inside each module: api (controllers + DTOs), application (services), domain (entities, enums), infrastructure (repositories, external clients).
- Modules talk only through application services or Spring events, never through another module's repository.
- Controllers are thin and never return entities.
- External services (Taobao API, translation, SMS, Telegram, Payme, Click) are behind interfaces,
  with a real implementation and a Fake implementation used by the "dev" profile.
- Secrets only from environment variables. Never in code.

## API conventions
- Base path /api/v1; staff endpoints /api/v1/admin/**.
- JSON camelCase; pagination with page/size and PageResponse<T>.
- Errors: RFC 7807 ProblemDetail from one @RestControllerAdvice, with a "code" field and an Uzbek (Latin) message,
  e.g. {"code":"CART_ITEM_NOT_FOUND","detail":"Savatda bunday tovar yo'q"}.
- Texts support uz (default) and ru via messages_uz.properties / messages_ru.properties.

## How to work
- Do only the current task. Do not refactor unrelated code.
- Every business rule has unit tests; every controller has at least one test.
- Run ./mvnw verify before saying a task is done. All tests must pass.
- If something is unclear, ask instead of guessing.
- At the end of each task list: files created/changed, new endpoints, new env variables, TODOs.
- Write that end-of-task report in Uzbek (Latin). Code, commands and file names stay in English.
