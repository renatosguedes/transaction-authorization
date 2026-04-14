package com.example.authorization.infrastructure.kafka.producer;

import com.example.authorization.domain.model.Transaction;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionEvent(
        String transactionId,
        String accountId,
        String merchantId,
        BigDecimal amount,
        String currency,
        Instant createdAt
) {
    public static TransactionEvent from(Transaction transaction) {
        return new TransactionEvent(
                transaction.transactionId(),
                transaction.accountId(),
                transaction.merchantId(),
                transaction.amount().amount(),
                transaction.amount().currency(),
                transaction.createdAt()
        );
    }
}
