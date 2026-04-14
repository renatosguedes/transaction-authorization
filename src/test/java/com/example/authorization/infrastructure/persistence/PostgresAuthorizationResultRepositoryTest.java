package com.example.authorization.infrastructure.persistence;

import com.example.authorization.domain.model.AuthorizationResult;
import com.example.authorization.infrastructure.persistence.entity.AuthorizationResultEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostgresAuthorizationResultRepositoryTest {

    @Mock
    SpringDataAuthorizationResultRepository jpaRepo;

    PostgresAuthorizationResultRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PostgresAuthorizationResultRepository(jpaRepo);
    }

    @Test
    void should_save_entity_when_result_is_approved() {
        AuthorizationResult result = new AuthorizationResult(
                "txn-001", "acc-123", AuthorizationResult.Status.APPROVED, null, Instant.parse("2026-01-01T10:00:00Z")
        );
        when(jpaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        repository.save(result);

        ArgumentCaptor<AuthorizationResultEntity> captor = ArgumentCaptor.forClass(AuthorizationResultEntity.class);
        verify(jpaRepo).save(captor.capture());
        AuthorizationResultEntity entity = captor.getValue();
        assertThat(entity.getTransactionId()).isEqualTo("txn-001");
        assertThat(entity.getAccountId()).isEqualTo("acc-123");
        assertThat(entity.getStatus()).isEqualTo("APPROVED");
        assertThat(entity.getReason()).isNull();
        assertThat(entity.getProcessedAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
    }

    @Test
    void should_save_entity_when_result_is_denied_with_reason() {
        AuthorizationResult result = new AuthorizationResult(
                "txn-002", "acc-456", AuthorizationResult.Status.DENIED, "Rate limit exceeded", Instant.parse("2026-01-01T10:01:00Z")
        );
        when(jpaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        repository.save(result);

        ArgumentCaptor<AuthorizationResultEntity> captor = ArgumentCaptor.forClass(AuthorizationResultEntity.class);
        verify(jpaRepo).save(captor.capture());
        AuthorizationResultEntity entity = captor.getValue();
        assertThat(entity.getStatus()).isEqualTo("DENIED");
        assertThat(entity.getReason()).isEqualTo("Rate limit exceeded");
    }

    @Test
    void should_return_empty_when_transaction_not_found() {
        when(jpaRepo.findByTransactionId("txn-999")).thenReturn(Optional.empty());

        Optional<AuthorizationResult> found = repository.findByTransactionId("txn-999");

        assertThat(found).isEmpty();
    }

    @Test
    void should_return_domain_result_when_transaction_found() {
        Instant processedAt = Instant.parse("2026-01-01T10:00:00Z");
        AuthorizationResultEntity entity = new AuthorizationResultEntity(
                "txn-001", "acc-123", "APPROVED", null, processedAt
        );
        when(jpaRepo.findByTransactionId("txn-001")).thenReturn(Optional.of(entity));

        Optional<AuthorizationResult> found = repository.findByTransactionId("txn-001");

        assertThat(found).isPresent();
        AuthorizationResult result = found.get();
        assertThat(result.transactionId()).isEqualTo("txn-001");
        assertThat(result.accountId()).isEqualTo("acc-123");
        assertThat(result.status()).isEqualTo(AuthorizationResult.Status.APPROVED);
        assertThat(result.reason()).isNull();
        assertThat(result.processedAt()).isEqualTo(processedAt);
    }
}
