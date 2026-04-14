package com.example.authorization.infrastructure.kafka.streams;

import java.time.Instant;

public record AuthorizationResultEvent(
        String transactionId,
        String accountId,
        String status,       // "APPROVED" or "DENIED"
        String reason,       // null for APPROVED; DenialReason.getDescription() for DENIED
        Instant processedAt
) {}
