package com.example.authorization.domain.port.outbound;

import com.example.authorization.domain.model.AuthorizationResult;

import java.util.Optional;

public interface AuthorizationResultRepository {
    void save(AuthorizationResult result);
    Optional<AuthorizationResult> findByTransactionId(String transactionId);
}
