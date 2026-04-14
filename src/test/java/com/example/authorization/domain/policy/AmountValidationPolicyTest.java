package com.example.authorization.domain.policy;

import com.example.authorization.domain.model.AuthorizationResult;
import com.example.authorization.domain.model.DenialReason;
import com.example.authorization.domain.model.Money;
import com.example.authorization.domain.model.Transaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AmountValidationPolicyTest {

    final AmountValidationPolicy policy = new AmountValidationPolicy();

    final Transaction base = new Transaction(
            "txn-001", "acc-123", "merchant-1",
            Money.of(new BigDecimal("100.00"), "BRL"), Instant.now());

    private Transaction withAmount(String amount) {
        return new Transaction(
                base.transactionId(), base.accountId(), base.merchantId(),
                Money.of(new BigDecimal(amount), "BRL"), base.createdAt());
    }

    // --- Denial cases ---

    @Test
    void should_deny_when_amount_is_zero() {
        Optional<AuthorizationResult> result = policy.evaluate(withAmount("0.00"));

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(AuthorizationResult.Status.DENIED);
    }

    @Test
    void should_deny_when_amount_is_negative() {
        Optional<AuthorizationResult> result = policy.evaluate(withAmount("-1.00"));

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(AuthorizationResult.Status.DENIED);
    }

    @Test
    void should_deny_when_amount_exceeds_maximum() {
        Optional<AuthorizationResult> result = policy.evaluate(withAmount("50000.01"));

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(AuthorizationResult.Status.DENIED);
    }

    @Test
    void should_return_invalid_amount_denial_reason_for_zero() {
        Optional<AuthorizationResult> result = policy.evaluate(withAmount("0.00"));

        assertThat(result.get().reason()).isEqualTo(DenialReason.INVALID_AMOUNT.getDescription());
    }

    @Test
    void should_return_invalid_amount_denial_reason_for_excess() {
        Optional<AuthorizationResult> result = policy.evaluate(withAmount("50000.01"));

        assertThat(result.get().reason()).isEqualTo(DenialReason.INVALID_AMOUNT.getDescription());
    }

    // --- Approval cases ---

    @Test
    void should_approve_when_amount_is_one_cent() {
        Optional<AuthorizationResult> result = policy.evaluate(withAmount("0.01"));

        assertThat(result).isEmpty();
    }

    @Test
    void should_approve_when_amount_is_within_limit() {
        Optional<AuthorizationResult> result = policy.evaluate(withAmount("100.00"));

        assertThat(result).isEmpty();
    }

    @Test
    void should_approve_when_amount_equals_maximum() {
        Optional<AuthorizationResult> result = policy.evaluate(withAmount("50000.00"));

        assertThat(result).isEmpty();
    }
}
