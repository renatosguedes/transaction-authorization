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
