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
