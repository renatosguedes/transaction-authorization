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
