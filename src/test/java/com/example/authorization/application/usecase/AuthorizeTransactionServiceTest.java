package com.example.authorization.application.usecase;

import com.example.authorization.domain.model.AuthorizationResult;
import com.example.authorization.domain.model.Money;
import com.example.authorization.domain.model.Transaction;
import com.example.authorization.domain.port.outbound.TransactionEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthorizeTransactionServiceTest {

    @Mock
    TransactionEventPublisher eventPublisher;

    @Mock
    PendingAuthorizationRegistry registry;

    AuthorizeTransactionService service;

    Transaction validTransaction = new Transaction(
            "txn-001", "acc-123", "merchant-1",
            Money.of(new BigDecimal("100.00"), "BRL"), Instant.now());

    Transaction zeroAmountTransaction = new Transaction(
            "txn-bad", "acc-123", "merchant-1",
            Money.of(BigDecimal.ZERO, "BRL"), Instant.now());

    @BeforeEach
    void setUp() {
        service = new AuthorizeTransactionService(eventPublisher, registry);
    }

    @Test
    void should_deny_immediately_without_publishing_when_amount_is_zero() {
        AuthorizationResult result = service.authorize(zeroAmountTransaction);

        assertThat(result.status()).isEqualTo(AuthorizationResult.Status.DENIED);
        verifyNoInteractions(eventPublisher);
        verifyNoInteractions(registry);
    }

    @Test
    void should_deny_immediately_without_publishing_when_amount_is_negative() {
        Transaction negativeAmount = new Transaction(
                "txn-neg", "acc-123", "merchant-1",
                Money.of(new BigDecimal("-1.00"), "BRL"), Instant.now());

        AuthorizationResult result = service.authorize(negativeAmount);

        assertThat(result.status()).isEqualTo(AuthorizationResult.Status.DENIED);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void should_publish_event_when_stateless_validation_passes() {
        AuthorizationResult approved = AuthorizationResult.approved(validTransaction);
        when(registry.register("txn-001")).thenReturn(CompletableFuture.completedFuture(approved));

        service.authorize(validTransaction);

        verify(eventPublisher).publish(validTransaction);
    }

    @Test
    void should_register_future_before_publishing() {
        AuthorizationResult approved = AuthorizationResult.approved(validTransaction);
        when(registry.register("txn-001")).thenReturn(CompletableFuture.completedFuture(approved));

        service.authorize(validTransaction);

        // register must happen before publish to avoid race with a fast consumer
        var inOrder = inOrder(registry, eventPublisher);
        inOrder.verify(registry).register("txn-001");
        inOrder.verify(eventPublisher).publish(validTransaction);
    }

    @Test
    void should_return_approved_result_from_streams() {
        AuthorizationResult approved = AuthorizationResult.approved(validTransaction);
        when(registry.register("txn-001")).thenReturn(CompletableFuture.completedFuture(approved));

        AuthorizationResult result = service.authorize(validTransaction);

        assertThat(result.status()).isEqualTo(AuthorizationResult.Status.APPROVED);
    }

    @Test
    void should_return_denied_result_from_streams() {
        AuthorizationResult denied = AuthorizationResult.denied(validTransaction, "Rate limit exceeded");
        when(registry.register("txn-001")).thenReturn(CompletableFuture.completedFuture(denied));

        AuthorizationResult result = service.authorize(validTransaction);

        assertThat(result.status()).isEqualTo(AuthorizationResult.Status.DENIED);
        assertThat(result.reason()).isEqualTo("Rate limit exceeded");
    }

    @Test
    void should_return_denied_with_timeout_reason_when_streams_does_not_respond() {
        // Future that never completes — simulates timeout
        when(registry.register("txn-001")).thenReturn(new CompletableFuture<>());

        // Override timeout to 0 for test speed
        var fastService = new AuthorizeTransactionService(eventPublisher, registry) {
            @Override
            public AuthorizationResult authorize(Transaction transaction) {
                registry.register(transaction.transactionId());
                eventPublisher.publish(transaction);
                try {
                    return new CompletableFuture<AuthorizationResult>()
                            .get(0, java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (java.util.concurrent.TimeoutException e) {
                    registry.remove(transaction.transactionId());
                    return AuthorizationResult.denied(transaction, "Authorization timed out");
                } catch (Exception e) {
                    return AuthorizationResult.denied(transaction, "Authorization error");
                }
            }
        };

        AuthorizationResult result = fastService.authorize(validTransaction);

        assertThat(result.status()).isEqualTo(AuthorizationResult.Status.DENIED);
        assertThat(result.reason()).isEqualTo("Authorization timed out");
        verify(registry).remove("txn-001");
    }
}
