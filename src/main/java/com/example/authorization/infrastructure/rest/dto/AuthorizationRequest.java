package com.example.authorization.infrastructure.rest.dto;

import java.math.BigDecimal;

public record AuthorizationRequest(
        String transactionId,
        String accountId,
        String merchantId,
        BigDecimal amount,
        String currency
) {}
