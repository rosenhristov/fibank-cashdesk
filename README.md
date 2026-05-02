# Cash Desk Module

A Spring Boot 3 REST API for managing cash operations — deposits, withdrawals, and balance queries — across multiple cashiers in BGN and EUR currencies. Balances and transaction history are stored in plain pipe-delimited text files; no database is required.

---

## Prerequisites

| Tool  | Version |
|-------|---------|
| Java  | 17+     |
| Maven | 3.8+    |

---

## Quick Start

```bash
# 1 — clone
git clone https://github.com/<your-org>/cash-desk-module.git
cd cash-desk-module

# 2 — build (compiles and runs all tests)
mvn clean package

# 3 — run
java -jar target/cash-desk-module-1.0.0.jar
```

The server starts on **http://localhost:8080**.

### First boot

On startup, `DataInitializer` checks whether the three required cashiers exist in `cash_balances.txt`. If they are absent it seeds them with the specification-mandated starting balances:

| Cashier | BGN   | BGN Denominations               | EUR   | EUR Denominations                |
|---------|-------|---------------------------------|-------|----------------------------------|
| MARTINA | 1 000 | 50 × 10-BGN + 10 × 50-BGN     | 2 000 | 100 × 10-EUR + 20 × 50-EUR     |
| PETER   | 1 000 | 50 × 10-BGN + 10 × 50-BGN     | 2 000 | 100 × 10-EUR + 20 × 50-EUR     |
| LINDA   | 1 000 | 50 × 10-BGN + 10 × 50-BGN     | 2 000 | 100 × 10-EUR + 20 × 50-EUR     |

Subsequent restarts leave all live balances completely untouched.

---

## Authentication

Every request to `/api/**` must include the header `FIB-X-AUTH` containing the API key for authentication, provided by 
the team, that should be stored in an environment variable `${APP_AUTH_API_KEY}` for security reasons:

```
FIB-X-AUTH: ${APP_AUTH_API_KEY}
```

Requests without this header, or with an incorrect value, receive `401 Unauthorized` before reaching any controller. 
The environment variable is set as a value of the `app.auth.api-key` property in `application.properties`.

---

## API Endpoints

### `POST /api/v1/cash-operation`

Executes a deposit or withdrawal. Both operation types share this single endpoint — the `operationType` field in the request body distinguishes them.

**Request body:**

```json
{
  "cashierName": "MARTINA",
  "operationType": "DEPOSIT",
  "currency": "BGN",
  "amount": 600,
  "denominations": [
    { "faceValue": 10, "count": 10 },
    { "faceValue": 50, "count": 10 }
  ]
}
```

| Field           | Type                        | Constraints                                                             |
|-----------------|-----------------------------|-------------------------------------------------------------------------|
| `cashierName`   | string                      | Required, not blank; matched case-insensitively                         |
| `operationType` | `DEPOSIT` \| `WITHDRAWAL`   | Required                                                                |
| `currency`      | `BGN` \| `EUR`              | Required                                                                |
| `amount`        | positive integer            | Must equal the arithmetic sum of `faceValue × count` across all denominations |
| `denominations` | array of `{faceValue,count}` | At least one entry; both `faceValue` and `count` must be positive integers |

**Response `200 OK`:**

```json
{
  "cashierName": "MARTINA",
  "operationType": "DEPOSIT",
  "currency": "BGN",
  "newBalance": 1600,
  "updatedBalance": {
    "total": 1600,
    "denominations": [
      { "faceValue": 10, "count": 60 },
      { "faceValue": 50, "count": 20 }
    ]
  }
}
```

The response returns the updated state for the affected currency only.

---

### `GET /api/v1/cash-balance`

Returns current cashier balances with denomination breakdowns. All three query parameters are optional and may be combined freely. They are bound as a `CashBalanceRequest` record via Spring MVC `@ModelAttribute`.

| Parameter  | Type   | Format       | Description                                       |
|------------|--------|--------------|---------------------------------------------------|
| `cashier`  | string | —            | Filter to a single cashier (case-insensitive)     |
| `dateFrom` | date   | `yyyy-MM-dd` | Inclusive start date; echoed back in the response |
| `dateTo`   | date   | `yyyy-MM-dd` | Inclusive end date; echoed back in the response   |

**Examples:**

```
GET /api/v1/cash-balance
GET /api/v1/cash-balance?cashier=MARTINA
GET /api/v1/cash-balance?cashier=PETER&dateFrom=2025-01-01&dateTo=2025-12-31
```

**Response `200 OK`:**

```json
{
  "cashiers": [
    {
      "cashierName": "MARTINA",
      "bgn": {
        "total": 1500,
        "denominations": [
          { "faceValue": 10, "count": 55 },
          { "faceValue": 50, "count": 19 }
        ]
      },
      "eur": {
        "total": 1700,
        "denominations": [
          { "faceValue": 10, "count": 100 },
          { "faceValue": 20, "count": 5 },
          { "faceValue": 50, "count": 12 }
        ]
      }
    }
  ],
  "cashier": "MARTINA",
  "dateFrom": null,
  "dateTo": null
}
```

Denominations are sorted ascending by face value. The `cashier`, `dateFrom`, and `dateTo` fields at the top level echo 
whichever filters were applied (or `null` if omitted).

---

## HTTP Status Codes

| Code | Trigger                                                                       |
|------|-------------------------------------------------------------------------------|
| 200  | Operation or query succeeded                                                  |
| 400  | Bean validation failure (missing/blank field, invalid enum) or malformed JSON |
| 401  | Missing or incorrect `FIB-X-AUTH` header                                      |
| 404  | Cashier name supplied but not found in the system                             |
| 422  | Business rule violated: amount/denomination sum mismatch, insufficient funds, unknown denomination face value |
| 500  | Storage I/O failure or unexpected server error                                |

**Error response body (all non-2xx):**

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Request validation failed — see 'violations' for details",
  "timestamp": "2025-04-29T10:00:00",
  "violations": [
    { "field": "cashierName", "message": "cashierName must not be blank" },
    { "field": "amount",      "message": "amount must be a positive integer" }
  ]
}
```

The `violations` array is present only for `400` validation errors. For `422`, `404`, and `500` responses it is omitted 
entirely.

---

## Spec Scenario and Expected Final Balances

Run the four spec operations in order against a freshly started instance:

| # | Operation  | Cashier | Currency | Amount | Denominations              |
|---|------------|---------|----------|--------|----------------------------|
| 1 | DEPOSIT    | MARTINA | BGN      | 600    | 10×10-BGN, 10×50-BGN      |
| 2 | DEPOSIT    | MARTINA | EUR      | 200    | 5×20-EUR, 2×50-EUR         |
| 3 | WITHDRAWAL | MARTINA | BGN      | 100    | 5×10-BGN, 1×50-BGN        |
| 4 | WITHDRAWAL | MARTINA | EUR      | 500    | 10×50-EUR                  |

**Expected final totals for MARTINA:**

| Currency | Start | +Deposit | −Withdrawal | **Final** |
|----------|-------|----------|-------------|-----------|
| BGN      | 1 000 | +600     | −100        | **1 500** |
| EUR      | 2 000 | +200     | −500        | **1 700** |

**Expected denomination counts for MARTINA:**

| Currency | Face Value | Start | Deposit | Withdrawal | **Final** |
|----------|-----------|-------|---------|------------|-----------|
| BGN      | 10        | 50    | +10     | −5         | **55**    |
| BGN      | 50        | 10    | +10     | −1         | **19**    |
| EUR      | 10        | 100   | —       | —          | **100**   |
| EUR      | 20        | 0     | +5      | —          | **5**     |
| EUR      | 50        | 20    | +2      | −10        | **12**    |

PETER and LINDA are untouched and remain at 1 000 BGN / 2 000 EUR each.

---

## Postman Collection

The `postman/` directory contains a ready-to-use collection and environment.

**Import steps:**

1. Open Postman → **Import**
2. Import `postman/CashDesk.postman_collection.json`
3. Import `postman/CashDesk.postman_environment.json`
4. Select **Cash Desk — Local** from the environment dropdown

A collection-level pre-request script injects the `FIB-X-AUTH` header automatically on every request using `{{apiKey}}` from the environment — no per-request configuration needed.

**Included requests:**

| # | Method | Path                      | Description                     |
|---|--------|---------------------------|---------------------------------|
| 1 | POST   | `/api/v1/cash-operation`  | Deposit BGN 600 — MARTINA      |
| 2 | POST   | `/api/v1/cash-operation`  | Deposit EUR 200 — MARTINA      |
| 3 | POST   | `/api/v1/cash-operation`  | Withdrawal BGN 100 — MARTINA   |
| 4 | POST   | `/api/v1/cash-operation`  | Withdrawal EUR 500 — MARTINA   |
| 5 | GET    | `/api/v1/cash-balance`    | Balance — all cashiers          |
| 6 | GET    | `/api/v1/cash-balance`    | Balance — MARTINA only          |
| 7 | GET    | `/api/v1/cash-balance`    | Balance — date range filter     |

Each request includes Postman test scripts that assert the correct status codes, balance totals, and denomination counts.

---

## Data Files

Both files are created automatically on first boot if absent. They live in `src/main/resources/data/`.

### `cash_balances.txt` — live balance state

Rewritten in full after every operation via an atomic write (written to a `.tmp` file, then renamed over the original). One row per denomination slot, grouped by cashier then currency, sorted ascending by face value within each group.

```
# CASHIER|CURRENCY|FACE_VALUE|COUNT
MARTINA|BGN|10|55
MARTINA|BGN|50|19
MARTINA|EUR|10|100
MARTINA|EUR|20|5
MARTINA|EUR|50|12
PETER|BGN|10|50
PETER|BGN|50|10
PETER|EUR|10|100
PETER|EUR|50|20
...
```

### `transactions.txt` — append-only audit log

One line appended per completed operation. Lines are never modified or deleted after writing.

```
# TIMESTAMP|CASHIER|TYPE|CURRENCY|AMOUNT|DENOMINATIONS
2025-04-29T10:00:00|MARTINA|DEPOSIT|BGN|600|10x10,50x10
2025-04-29T10:05:00|MARTINA|DEPOSIT|EUR|200|20x5,50x2
2025-04-29T10:10:00|MARTINA|WITHDRAWAL|BGN|100|10x5,50x1
2025-04-29T10:15:00|MARTINA|WITHDRAWAL|EUR|500|50x10
```

The `DENOMINATIONS` column uses the format `faceValue x count` — for example, `10x10` means ten 10-unit bills (100 units total), and `50x10` means ten 50-unit bills (500 units total). Multiple denominations are comma-separated.

Lines beginning with `#` and blank lines are silently skipped by the parser. Malformed data lines are logged as warnings and skipped without stopping the application.

---

## Configuration

All configurable values are in `src/main/resources/application.properties`:

```properties
server.port=8080

# API key required in the FIB-X-AUTH header on every request - for security reasons
# the value of the API key should not be stored in the repo, it should be added in an environment variable
# ${APP_AUTH_API_KEY}, used as a value of the 'app.auth.api-key' property in the application.properties file 
app.auth.api-key=${APP_AUTH_API_KEY}

# Paths to the two data files (relative to the project root)
app.data.balances-file=src/main/resources/data/cash_balances.txt
app.data.transactions-file=src/main/resources/data/transactions.txt

# Log level for all application classes
logging.level.bg.fibank.cashdesk=INFO
```

---

## Project Structure

```
cashdesk/
├── postman/
│   ├── CashDesk.postman_collection.json
│   └── CashDesk.postman_environment.json
├── src/
│   ├── main/
│   │   ├── java/bg/fibank/cashdesk/
│   │   │   ├── CashDeskApplication.java              ← entry point
│   │   │   ├── config/
│   │   │   │   ├── AppProperties.java                ← typed binding for all app.* properties
│   │   │   │   └── SecurityConfig.java               ← registers AuthHeaderFilter for /api/**
│   │   │   ├── controller/
│   │   │   │   ├── CashBalanceController.java        ← GET  /api/v1/cash-balance
│   │   │   │   └── CashOperationController.java      ← POST /api/v1/cash-operation
│   │   │   ├── dto/
│   │   │   │   ├── CashBalanceRequest.java           ← @ModelAttribute query param record
│   │   │   │   ├── CashBalanceResponse.java          ← balance query response
│   │   │   │   ├── CashierBalanceDto.java            ← one cashier's BGN + EUR balances
│   │   │   │   ├── CashOperationRequest.java         ← validated @RequestBody for operations
│   │   │   │   ├── CashOperationResponse.java        ← operation result with updated balance
│   │   │   │   ├── CurrencyBalanceDto.java           ← total + denominations for one currency
│   │   │   │   ├── DenominationDto.java              ← {faceValue, count} pair
│   │   │   │   └── ErrorResponse.java                ← uniform error envelope (with optional violations)
│   │   │   ├── exception/
│   │   │   │   ├── CashDeskException.java            ← abstract base; each subtype carries its HTTP status
│   │   │   │   ├── CashierNotFoundException.java      ← 404
│   │   │   │   ├── DenominationNotFoundException.java ← 422
│   │   │   │   ├── GlobalExceptionHandler.java       ← @RestControllerAdvice; maps all exceptions to JSON
│   │   │   │   ├── InsufficientFundsException.java   ← 422
│   │   │   │   └── InvalidOperationException.java    ← 422
│   │   │   ├── filter/
│   │   │   │   └── AuthHeaderFilter.java             ← validates FIB-X-AUTH before every /api/** request
│   │   │   ├── init/
│   │   │   │   ├── CashierSeed.java                  ← immutable seed descriptor (name + denominations)
│   │   │   │   └── DataInitializer.java              ← ApplicationRunner; seeds cashiers on first boot only
│   │   │   ├── model/
│   │   │   │   ├── CashierBalance.java               ← aggregate; owns live denomination state per currency
│   │   │   │   ├── Currency.java                     ← enum: BGN | EUR
│   │   │   │   ├── Denomination.java                 ← mutable value object: faceValue + count
│   │   │   │   ├── OperationType.java                ← enum: DEPOSIT | WITHDRAWAL
│   │   │   │   └── Transaction.java                  ← immutable record of one completed operation
│   │   │   ├── repository/
│   │   │   │   ├── BalanceFileRepository.java        ← in-memory cache + atomic full-rewrite on save
│   │   │   │   ├── FileFormat.java                   ← all encode/decode logic for both text files
│   │   │   │   ├── FileFormatException.java          ← thrown when a file line cannot be parsed
│   │   │   │   └── TransactionFileRepository.java    ← append-only writes + filtered reads
│   │   │   └── service/
│   │   │       └── CashDeskService.java              ← business logic; per-cashier locking
│   │   └── resources/
│   │       ├── application.properties
│   │       └── data/
│   │           ├── cash_balances.txt                 ← live balance state (rewritten atomically)
│   │           └── transactions.txt                  ← append-only audit log
│   └── test/
│       └── java/bg/fibank/cashdesk/
│           ├── controller/
│           │   ├── CashBalanceControllerTest.java    ← @WebMvcTest; auth, binding, filters, errors
│           │   └── CashOperationControllerTest.java  ← @WebMvcTest; auth, validation, domain errors
│           ├── dto/
│           │   └── CashBalanceRequestTest.java       ← record construction and field accessor names
│           ├── exception/
│           │   └── GlobalExceptionHandlerTest.java   ← all five handler branches, direct unit tests
│           ├── filter/
│           │   └── AuthHeaderFilterTest.java         ← missing header, wrong key, valid key
│           ├── init/
│           │   └── DataInitializerTest.java          ← first boot, idempotency, persistence round-trip
│           ├── model/
│           │   ├── CashierBalanceTest.java           ← add/subtract, atomicity, defensive copy
│           │   ├── DenominationTest.java             ← total(), copy(), toString()
│           │   ├── ExceptionHierarchyTest.java       ← HTTP status codes and message content
│           │   └── TransactionTest.java              ← record construction, serialisation round-trip
│           ├── repository/
│           │   ├── BalanceFileRepositoryTest.java    ← load, save, atomic rewrite, restart round-trip
│           │   ├── FileFormatTest.java               ← encode/decode round-trips, error paths
│           │   └── TransactionFileRepositoryTest.java ← append, all filter combinations, resilience
│           └── service/
│               └── CashDeskServiceTest.java          ← full spec scenario, locking, all error paths
└── pom.xml
```

---

## Technology Stack

| Concern        | Choice                                                                 |
|----------------|------------------------------------------------------------------------|
| Language       | Java 17                                                                |
| Framework      | Spring Boot 3.2.5                                                      |
| Web            | `spring-boot-starter-web`                                              |
| Validation     | `spring-boot-starter-validation` (Jakarta Bean Validation)             |
| Boilerplate    | Lombok (`@Slf4j`, `@RequiredArgsConstructor`, `@Data`, etc.)           |
| Logging        | SLF4J via Logback (bundled with Spring Boot)                           |
| Persistence    | Plain pipe-delimited text files — no database, no ORM                  |
| Build          | Maven 3.8+                                                             |
| Testing        | JUnit 5, AssertJ, Mockito, Spring MockMvc (`@WebMvcTest`), `@TempDir` |

---

## Logging

Every significant event is logged via SLF4J:

| Level | Events                                                                     |
|-------|----------------------------------------------------------------------------|
| INFO  | Each deposit / withdrawal — cashier, type, currency, amount, denominations |
| INFO  | Each balance query — filters applied, number of results                    |
| INFO  | Startup — cashier seed status, file paths, initial balances                |
| WARN  | Auth rejections — missing or wrong `FIB-X-AUTH` header                    |
| WARN  | Domain errors (404, 422) — includes the full error message                 |
| WARN  | Malformed lines in data files — skipped without stopping the application   |
| ERROR | I/O failures and unexpected exceptions — includes full stack trace         |

The log level is configurable via `logging.level.bg.fibank.cashdesk` in `application.properties`.