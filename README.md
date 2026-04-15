# Transaction Authorization Microservice

A financial transaction authorization microservice built with Spring Boot and Kafka Streams.
It exposes a synchronous HTTP API that internally routes transactions through a Kafka Streams
topology — returning the real authorization decision to the caller.

## Stack

- Java 21 (LTS)
- Spring Boot 3.x
- Spring Kafka + Kafka Streams
- PostgreSQL (result persistence via JPA + Flyway)
- Docker Compose (Kafka, Zookeeper, PostgreSQL, Kafka UI)
- JUnit 5 + TopologyTestDriver

---

## Architecture

The service follows **Hexagonal Architecture** (Ports & Adapters). The domain layer has zero
external dependencies — Kafka, Spring, and PostgreSQL are infrastructure details the domain
never sees.

```
┌──────────────────────────────────────────────────────────────────────┐
│                          HTTP Client                                 │
└─────────────────────────────┬────────────────────────────────────────┘
                              │ POST /api/v1/authorize
                              ▼
┌──────────────────────────────────────────────────────────────────────┐
│  infrastructure / rest                                               │
│                                                                      │
│  AuthorizationController ──► KafkaStreamsHealthGuard (503 guard)     │
└─────────────────────────────┬────────────────────────────────────────┘
                              │ AuthorizeTransactionUseCase (port)
                              ▼
┌──────────────────────────────────────────────────────────────────────┐
│  application / usecase                                               │
│                                                                      │
│  AuthorizeTransactionService                                         │
│    1. AmountValidationPolicy  (stateless check)                      │
│    2. TransactionEventPublisher.publish()  (port)                    │
│    3. PendingAuthorizationRegistry.await()  (CompletableFuture)      │
└──────┬──────────────────────────────────────────┬────────────────────┘
       │ publish event                            │ await result (≤5 s)
       ▼                                          ▲
┌─────────────────────────────────┐   ┌────────────────────────────────┐
│  infrastructure / kafka         │   │  infrastructure / kafka        │
│  producer                       │   │  consumer                      │
│                                 │   │                                │
│  KafkaTransactionEventPublisher │   │  AuthorizationResultConsumer   │
│  → authorization-requests       │   │  ← authorization-approved      │
└─────────────┬───────────────────┘   │  ← authorization-denied        │
              │                       └────────────────────────────────┘
              ▼                                    ▲
┌──────────────────────────────────────────────────────────────────────┐
│  infrastructure / kafka / streams                                    │
│                                                                      │
│  AuthorizationTopology (Kafka Streams)                               │
│                                                                      │
│  authorization-requests                                              │
│       │                                                              │
│       ├─[parse error]──────────────────► authorization-dlq           │
│       │                                                              │
│       └─[valid]──► RateLimitProcessor ──┬─[ok]──► authorization-approved
│                    (WindowStore,        │                            │
│                     60 s / 3 tx)        └─[over limit]──► authorization-denied
└──────────────────────────────────────────────────────────────────────┘
              │
              ▼
┌──────────────────────────────────────────────────────────────────────┐
│  infrastructure / persistence                                        │
│                                                                      │
│  PostgresAuthorizationResultRepository (saves every decision)        │
└──────────────────────────────────────────────────────────────────────┘
```

### Package layout

```
com.example.authorization
├── domain
│   ├── model          Transaction, AuthorizationResult, Money, DenialReason
│   ├── policy         AuthorizationPolicy, AmountValidationPolicy
│   └── port
│       ├── inbound    AuthorizeTransactionUseCase
│       └── outbound   TransactionEventPublisher, AuthorizationResultRepository
├── application
│   └── usecase        AuthorizeTransactionService, PendingAuthorizationRegistry
└── infrastructure
    ├── rest           AuthorizationController, KafkaStreamsHealthGuard, dtos/
    ├── kafka
    │   ├── producer   KafkaTransactionEventPublisher, TransactionEvent
    │   ├── streams    AuthorizationTopology, AuthorizationResultEvent
    │   └── consumer   AuthorizationResultConsumer
    └── persistence    PostgresAuthorizationResultRepository, entities/
```

---

## Business Rules

### Rule 1 — Basic validation (stateless)

Evaluated by `AmountValidationPolicy` before the event ever reaches Kafka.

| Condition | Decision |
|-----------|----------|
| Amount ≤ 0 | DENIED |
| Any required field missing | DENIED |
| Amount > R$ 50,000 | DENIED |
| Otherwise | continues to Rule 2 |

### Rule 2 — Rate limit (stateful, via Kafka Streams)

Evaluated inside `AuthorizationTopology` using an in-memory `WindowStore`.

| Condition | Decision |
|-----------|----------|
| More than 3 transactions in the last 60 seconds for the same `accountId` | DENIED |
| Otherwise | APPROVED |

---

## Kafka Topics

| Topic | Purpose |
|-------|---------|
| `authorization-requests` | Inbound — every incoming transaction |
| `authorization-approved` | Outbound — approved decisions |
| `authorization-denied` | Outbound — denied decisions |
| `authorization-dlq` | Dead Letter Queue — malformed / unparseable payloads |

Topics are **auto-created by the application** on startup via `KafkaTopicConfig`. Do not add
topic creation to `docker-compose.yml`.

---

## Running Locally

**Prerequisites:** Docker, Docker Compose, Java 21, Maven (via sdkman or system install).

```bash
# 1. Start infrastructure (Kafka, Zookeeper, PostgreSQL, Kafka UI)
docker-compose up -d

# 2. Start the application
mvn spring-boot:run
```

| Service | URL |
|---------|-----|
| REST API | http://localhost:8081 |
| Kafka UI | http://localhost:8080 |

> The first request after a cold start may return `503 Service Unavailable` while Kafka Streams
> reaches `RUNNING` state. This is intentional — retry after a few seconds.

---

## Sample Request

```bash
curl -X POST http://localhost:8081/api/v1/authorize \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "txn-001",
    "accountId": "acc-123",
    "amount": 150.00,
    "currency": "BRL",
    "merchantId": "merchant-456"
  }'
```

**Approved response:**
```json
{
  "transactionId": "txn-001",
  "status": "APPROVED",
  "reason": null
}
```

**Denied response (rate limit):**
```json
{
  "transactionId": "txn-001",
  "status": "DENIED",
  "reason": "Rate limit exceeded"
}
```

The endpoint is **synchronous** — it blocks for up to 5 seconds waiting for the Kafka Streams
decision and returns the real result. A timeout returns `DENIED` with reason
`"Authorization timed out"`. This design eliminates the need for client-side polling: the caller
receives the final decision in a single request, just like any ordinary REST call.

---

## Running Tests

```bash
# Run all tests and enforce 95% coverage on domain + application layers
mvn verify

# Open the HTML coverage report
open target/site/jacoco/index.html
```

Unit tests use JUnit 5 + Mockito. Topology tests use `TopologyTestDriver` — an in-process
test harness that runs the full Kafka Streams topology (including state stores and windowed
aggregations) without a real broker, no `@SpringBootTest`, no Testcontainers required.
