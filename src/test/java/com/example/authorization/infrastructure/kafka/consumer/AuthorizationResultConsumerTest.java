package com.example.authorization.infrastructure.kafka.consumer;

import com.example.authorization.application.usecase.PendingAuthorizationRegistry;
import com.example.authorization.domain.model.AuthorizationResult;
import com.example.authorization.domain.port.outbound.AuthorizationResultRepository;
import com.example.authorization.infrastructure.kafka.streams.AuthorizationResultEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AuthorizationResultConsumerTest {

    @Mock
    PendingAuthorizationRegistry registry;

    @Mock
    AuthorizationResultRepository repository;

    ObjectMapper objectMapper;

    AuthorizationResultConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        consumer = new AuthorizationResultConsumer(registry, repository, objectMapper);
    }

    @Test
    void should_complete_registry_and_persist_result_when_approved_event_received() throws Exception {
        Instant processedAt = Instant.parse("2026-01-01T10:00:00Z");
        AuthorizationResultEvent event = new AuthorizationResultEvent(
                "txn-001", "acc-123", "APPROVED", null, processedAt
        );
        String json = objectMapper.writeValueAsString(event);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("authorization-approved", 0, 0L, "txn-001", json);

        consumer.consume(record);

        ArgumentCaptor<AuthorizationResult> registryCaptor = ArgumentCaptor.forClass(AuthorizationResult.class);
        ArgumentCaptor<AuthorizationResult> repoCaptor = ArgumentCaptor.forClass(AuthorizationResult.class);
        verify(registry).complete(org.mockito.ArgumentMatchers.eq("txn-001"), registryCaptor.capture());
        verify(repository).save(repoCaptor.capture());
        AuthorizationResult saved = repoCaptor.getValue();
        assertThat(saved.transactionId()).isEqualTo("txn-001");
        assertThat(saved.accountId()).isEqualTo("acc-123");
        assertThat(saved.status()).isEqualTo(AuthorizationResult.Status.APPROVED);
        assertThat(saved.reason()).isNull();
        assertThat(saved.processedAt()).isEqualTo(processedAt);
    }

    @Test
    void should_persist_result_after_completing_registry() throws Exception {
        Instant processedAt = Instant.parse("2026-01-01T10:01:00Z");
        AuthorizationResultEvent event = new AuthorizationResultEvent(
                "txn-002", "acc-456", "DENIED", "Rate limit exceeded", processedAt
        );
        String json = objectMapper.writeValueAsString(event);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("authorization-denied", 0, 0L, "txn-002", json);

        consumer.consume(record);

        ArgumentCaptor<AuthorizationResult> captor = ArgumentCaptor.forClass(AuthorizationResult.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(AuthorizationResult.Status.DENIED);
        assertThat(captor.getValue().reason()).isEqualTo("Rate limit exceeded");
    }

    @Test
    void should_not_persist_when_message_is_invalid_json() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("authorization-approved", 0, 0L, "txn-003", "not-json");

        consumer.consume(record);

        verifyNoInteractions(repository);
        verifyNoInteractions(registry);
    }
}
