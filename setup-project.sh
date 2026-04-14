#!/bin/bash
# setup-project.sh
# Executa na pasta onde você quer criar o projeto

set -e

PROJECT="transaction-authorization"
BASE="$PROJECT/src/main/java/com/example/authorization"
TEST="$PROJECT/src/test/java/com/example/authorization"
RESOURCES="$PROJECT/src/main/resources"

echo "🚀 Criando estrutura do projeto $PROJECT..."

# Pastas de produção
mkdir -p "$BASE/domain/model"
mkdir -p "$BASE/domain/policy"
mkdir -p "$BASE/domain/port/inbound"
mkdir -p "$BASE/domain/port/outbound"
mkdir -p "$BASE/application/usecase"
mkdir -p "$BASE/infrastructure/rest/dto"
mkdir -p "$BASE/infrastructure/kafka/producer"
mkdir -p "$BASE/infrastructure/kafka/streams"
mkdir -p "$BASE/infrastructure/kafka/consumer"
mkdir -p "$BASE/infrastructure/persistence/entity"

# Pastas de teste
mkdir -p "$TEST/domain/policy"
mkdir -p "$TEST/application/usecase"
mkdir -p "$TEST/infrastructure/kafka/streams"

# Resources
mkdir -p "$RESOURCES"

# Copia o CLAUDE.md para a raiz do projeto
cp CLAUDE.md "$PROJECT/CLAUDE.md"

# ── domain/model ─────────────────────────────────────────────────────────────

cat > "$BASE/domain/model/Money.java" << 'EOF'
package com.example.authorization.domain.model;

import java.math.BigDecimal;

public record Money(BigDecimal amount, String currency) {

    public Money {
        if (amount == null) throw new IllegalArgumentException("amount não pode ser nulo");
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency não pode ser vazio");
    }

    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isGreaterThan(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Moedas diferentes: " + this.currency + " vs " + other.currency);
        }
        return this.amount.compareTo(other.amount) > 0;
    }

    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }
}
EOF

cat > "$BASE/domain/model/Transaction.java" << 'EOF'
package com.example.authorization.domain.model;

import java.time.Instant;
import java.util.Objects;

public record Transaction(
        String transactionId,
        String accountId,
        String merchantId,
        Money amount,
        Instant createdAt
) {
    public Transaction {
        Objects.requireNonNull(transactionId, "transactionId obrigatório");
        Objects.requireNonNull(accountId, "accountId obrigatório");
        Objects.requireNonNull(merchantId, "merchantId obrigatório");
        Objects.requireNonNull(amount, "amount obrigatório");
        Objects.requireNonNull(createdAt, "createdAt obrigatório");
    }
}
EOF

cat > "$BASE/domain/model/AuthorizationResult.java" << 'EOF'
package com.example.authorization.domain.model;

import java.time.Instant;

public record AuthorizationResult(
        String transactionId,
        String accountId,
        Status status,
        String reason,
        Instant processedAt
) {
    public enum Status { APPROVED, DENIED }

    public static AuthorizationResult approved(Transaction transaction) {
        return new AuthorizationResult(
                transaction.transactionId(),
                transaction.accountId(),
                Status.APPROVED,
                null,
                Instant.now()
        );
    }

    public static AuthorizationResult denied(Transaction transaction, String reason) {
        return new AuthorizationResult(
                transaction.transactionId(),
                transaction.accountId(),
                Status.DENIED,
                reason,
                Instant.now()
        );
    }
}
EOF

cat > "$BASE/domain/model/DenialReason.java" << 'EOF'
package com.example.authorization.domain.model;

public enum DenialReason {
    INVALID_AMOUNT("Valor inválido ou fora dos limites permitidos"),
    MISSING_FIELDS("Campos obrigatórios ausentes"),
    RATE_LIMIT_EXCEEDED("Limite de transações por período excedido");

    private final String description;

    DenialReason(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
EOF

# ── domain/policy ─────────────────────────────────────────────────────────────

cat > "$BASE/domain/policy/AuthorizationPolicy.java" << 'EOF'
package com.example.authorization.domain.policy;

import com.example.authorization.domain.model.AuthorizationResult;
import com.example.authorization.domain.model.Transaction;

import java.util.Optional;

/**
 * Contrato para políticas de autorização stateless.
 * Retorna um resultado de negação se a política for violada, ou empty se aprovado.
 */
public interface AuthorizationPolicy {
    Optional<AuthorizationResult> evaluate(Transaction transaction);
}
EOF

cat > "$BASE/domain/policy/AmountValidationPolicy.java" << 'EOF'
package com.example.authorization.domain.policy;

import com.example.authorization.domain.model.AuthorizationResult;
import com.example.authorization.domain.model.DenialReason;
import com.example.authorization.domain.model.Money;
import com.example.authorization.domain.model.Transaction;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Regra 1: valor ≤ 0 ou valor > R$50.000 → DENIED
 */
public class AmountValidationPolicy implements AuthorizationPolicy {

    private static final Money MAX_AMOUNT = Money.of(new BigDecimal("50000.00"), "BRL");

    @Override
    public Optional<AuthorizationResult> evaluate(Transaction transaction) {
        Money amount = transaction.amount();

        if (!amount.isPositive()) {
            return Optional.of(AuthorizationResult.denied(transaction, DenialReason.INVALID_AMOUNT.getDescription()));
        }

        if (amount.isGreaterThan(MAX_AMOUNT)) {
            return Optional.of(AuthorizationResult.denied(transaction, DenialReason.INVALID_AMOUNT.getDescription()));
        }

        return Optional.empty();
    }
}
EOF

# ── domain/port ───────────────────────────────────────────────────────────────

cat > "$BASE/domain/port/inbound/AuthorizeTransactionUseCase.java" << 'EOF'
package com.example.authorization.domain.port.inbound;

import com.example.authorization.domain.model.AuthorizationResult;
import com.example.authorization.domain.model.Transaction;

public interface AuthorizeTransactionUseCase {
    AuthorizationResult authorize(Transaction transaction);
}
EOF

cat > "$BASE/domain/port/outbound/TransactionEventPublisher.java" << 'EOF'
package com.example.authorization.domain.port.outbound;

import com.example.authorization.domain.model.Transaction;

public interface TransactionEventPublisher {
    void publish(Transaction transaction);
}
EOF

cat > "$BASE/domain/port/outbound/AuthorizationResultRepository.java" << 'EOF'
package com.example.authorization.domain.port.outbound;

import com.example.authorization.domain.model.AuthorizationResult;

import java.util.Optional;

public interface AuthorizationResultRepository {
    void save(AuthorizationResult result);
    Optional<AuthorizationResult> findByTransactionId(String transactionId);
}
EOF

# ── application/usecase ───────────────────────────────────────────────────────

cat > "$BASE/application/usecase/AuthorizeTransactionService.java" << 'EOF'
package com.example.authorization.application.usecase;

import com.example.authorization.domain.model.AuthorizationResult;
import com.example.authorization.domain.model.Transaction;
import com.example.authorization.domain.policy.AmountValidationPolicy;
import com.example.authorization.domain.policy.AuthorizationPolicy;
import com.example.authorization.domain.port.inbound.AuthorizeTransactionUseCase;
import com.example.authorization.domain.port.outbound.TransactionEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AuthorizeTransactionService implements AuthorizeTransactionUseCase {

    private final TransactionEventPublisher eventPublisher;
    private final List<AuthorizationPolicy> policies;

    public AuthorizeTransactionService(TransactionEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        // Políticas stateless — a stateful (rate limit) roda no Kafka Streams
        this.policies = List.of(new AmountValidationPolicy());
    }

    @Override
    public AuthorizationResult authorize(Transaction transaction) {
        // Avalia todas as políticas stateless
        Optional<AuthorizationResult> denial = policies.stream()
                .map(policy -> policy.evaluate(transaction))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();

        if (denial.isPresent()) {
            return denial.get();
        }

        // Passou nas validações stateless: publica no Kafka para processamento stateful
        eventPublisher.publish(transaction);

        // Retorna PENDING — o resultado final vem do Kafka Streams
        return AuthorizationResult.approved(transaction); // simplificado para o MVP
    }
}
EOF

# ── infrastructure/rest ───────────────────────────────────────────────────────

cat > "$BASE/infrastructure/rest/dto/AuthorizationRequest.java" << 'EOF'
package com.example.authorization.infrastructure.rest.dto;

import java.math.BigDecimal;

public record AuthorizationRequest(
        String transactionId,
        String accountId,
        String merchantId,
        BigDecimal amount,
        String currency
) {}
EOF

cat > "$BASE/infrastructure/rest/dto/AuthorizationResponse.java" << 'EOF'
package com.example.authorization.infrastructure.rest.dto;

import com.example.authorization.domain.model.AuthorizationResult;

import java.time.Instant;

public record AuthorizationResponse(
        String transactionId,
        String accountId,
        String status,
        String reason,
        Instant processedAt
) {
    public static AuthorizationResponse from(AuthorizationResult result) {
        return new AuthorizationResponse(
                result.transactionId(),
                result.accountId(),
                result.status().name(),
                result.reason(),
                result.processedAt()
        );
    }
}
EOF

cat > "$BASE/infrastructure/rest/AuthorizationController.java" << 'EOF'
package com.example.authorization.infrastructure.rest;

import com.example.authorization.domain.model.Money;
import com.example.authorization.domain.model.Transaction;
import com.example.authorization.domain.port.inbound.AuthorizeTransactionUseCase;
import com.example.authorization.infrastructure.rest.dto.AuthorizationRequest;
import com.example.authorization.infrastructure.rest.dto.AuthorizationResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1")
public class AuthorizationController {

    private final AuthorizeTransactionUseCase useCase;

    public AuthorizationController(AuthorizeTransactionUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping("/authorize")
    public ResponseEntity<AuthorizationResponse> authorize(@RequestBody AuthorizationRequest request) {
        var transaction = new Transaction(
                request.transactionId(),
                request.accountId(),
                request.merchantId(),
                Money.of(request.amount(), request.currency()),
                Instant.now()
        );

        var result = useCase.authorize(transaction);
        return ResponseEntity.ok(AuthorizationResponse.from(result));
    }
}
EOF

# ── resources ─────────────────────────────────────────────────────────────────

cat > "$RESOURCES/application.yml" << 'EOF'
server:
  port: 8081

spring:
  application:
    name: transaction-authorization
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      group-id: authorization-result-group
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      auto-offset-reset: earliest
    streams:
      application-id: authorization-streams
      default-key-serde: org.apache.kafka.common.serialization.Serdes$StringSerde
      default-value-serde: org.apache.kafka.common.serialization.Serdes$StringSerde
  datasource:
    url: jdbc:postgresql://localhost:5432/authorization
    username: postgres
    password: postgres
    driver-class-name: org.postgresql.Driver

kafka:
  topics:
    requests: authorization-requests
    approved: authorization-approved
    denied: authorization-denied
    dlq: authorization-dlq

logging:
  level:
    com.example.authorization: DEBUG
    org.apache.kafka: WARN
EOF

# ── docker-compose ────────────────────────────────────────────────────────────

cat > "$PROJECT/docker-compose.yml" << 'EOF'
version: '3.8'

services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
    ports:
      - "2181:2181"

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"

  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    depends_on:
      - kafka
    ports:
      - "8080:8080"
    environment:
      KAFKA_CLUSTERS_0_NAME: local
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:9092

  postgres:
    image: postgres:15
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: authorization
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    volumes:
      - postgres-data:/var/lib/postgresql/data

volumes:
  postgres-data:
EOF

# ── .gitignore ────────────────────────────────────────────────────────────────

cat > "$PROJECT/.gitignore" << 'EOF'
target/
.idea/
*.iml
.DS_Store
*.class
*.log
.env
EOF

echo ""
echo "✅ Estrutura criada com sucesso!"
echo ""
echo "Próximos passos:"
echo "  cd $PROJECT"
echo "  Cria o pom.xml (peça pro Claude Code gerar com as dependências certas)"
echo "  docker-compose up -d"
echo "  claude"
echo ""
echo "Dentro do Claude Code, comece com:"
echo "  'Gera o pom.xml com Spring Boot 3.x, Spring Kafka, Kafka Streams e PostgreSQL'"
