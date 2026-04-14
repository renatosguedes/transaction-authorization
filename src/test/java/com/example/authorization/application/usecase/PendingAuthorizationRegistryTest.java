package com.example.authorization.application.usecase;

import com.example.authorization.domain.model.AuthorizationResult;
import com.example.authorization.domain.model.Money;
import com.example.authorization.domain.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PendingAuthorizationRegistryTest {

    PendingAuthorizationRegistry registry;

    Transaction transaction = new Transaction(
            "txn-001", "acc-123", "merchant-1",
            Money.of(new BigDecimal("100"), "BRL"), Instant.now());

    @BeforeEach
    void setUp() {
        registry = new PendingAuthorizationRegistry();
    }

    @Test
    void should_return_future_that_completes_when_result_is_provided() throws Exception {
        CompletableFuture<AuthorizationResult> future = registry.register("txn-001");
        AuthorizationResult result = AuthorizationResult.approved(transaction);

        registry.complete("txn-001", result);

        assertThat(future.get(1, TimeUnit.SECONDS)).isEqualTo(result);
    }

    @Test
    void should_return_incomplete_future_before_result_arrives() {
        CompletableFuture<AuthorizationResult> future = registry.register("txn-001");

        assertThat(future.isDone()).isFalse();
    }

    @Test
    void should_do_nothing_when_completing_unknown_transaction() {
        AuthorizationResult result = AuthorizationResult.approved(transaction);

        // Should not throw
        registry.complete("unknown-txn", result);
    }

    @Test
    void should_remove_future_after_completion() throws Exception {
        registry.register("txn-001");
        registry.complete("txn-001", AuthorizationResult.approved(transaction));

        // Completing again should be a no-op, not throw
        registry.complete("txn-001", AuthorizationResult.approved(transaction));
    }

    @Test
    void should_remove_pending_future_on_explicit_remove() {
        CompletableFuture<AuthorizationResult> future = registry.register("txn-001");

        registry.remove("txn-001");

        // After removal, completing should be a no-op
        registry.complete("txn-001", AuthorizationResult.approved(transaction));
        assertThat(future.isDone()).isFalse();
    }

    @Test
    void should_handle_multiple_independent_transactions() throws Exception {
        CompletableFuture<AuthorizationResult> future1 = registry.register("txn-001");
        CompletableFuture<AuthorizationResult> future2 = registry.register("txn-002");

        AuthorizationResult result1 = AuthorizationResult.approved(transaction);
        AuthorizationResult result2 = AuthorizationResult.denied(transaction, "Rate limit exceeded");

        registry.complete("txn-001", result1);
        registry.complete("txn-002", result2);

        assertThat(future1.get(1, TimeUnit.SECONDS)).isEqualTo(result1);
        assertThat(future2.get(1, TimeUnit.SECONDS)).isEqualTo(result2);
    }
}
