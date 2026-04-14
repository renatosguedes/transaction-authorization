package com.example.authorization.domain.policy;

import com.example.authorization.domain.model.AuthorizationResult;
import com.example.authorization.domain.model.Transaction;

import java.util.Optional;

/**
 * Contrato para políticas de autorização stateless.
 * Retorna um resultado de negação se a política for violada, ou empty se aprovado.
 */
public interface AuthorizationPolicy {
    Optional<AuthorizationResult> evaluate(Transaction transaction);
}
