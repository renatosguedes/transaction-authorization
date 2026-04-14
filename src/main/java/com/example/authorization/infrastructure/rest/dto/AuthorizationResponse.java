package com.example.authorization.infrastructure.rest.dto;

import com.example.authorization.domain.model.AuthorizationResult;

import java.time.Instant;

public record AuthorizationResponse(
        String transactionId,
        String accountId,
        String status,
        String reason,
        Instant processedAt
) {
    public static AuthorizationResponse from(AuthorizationResult result) {
        return new AuthorizationResponse(
                result.transactionId(),
                result.accountId(),
                result.status().name(),
                result.reason(),
                result.processedAt()
        );
    }
}
