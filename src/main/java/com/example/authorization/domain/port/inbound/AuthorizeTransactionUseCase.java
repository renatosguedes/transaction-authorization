package com.example.authorization.domain.port.inbound;

import com.example.authorization.domain.model.AuthorizationResult;
import com.example.authorization.domain.model.Transaction;

public interface AuthorizeTransactionUseCase {
    AuthorizationResult authorize(Transaction transaction);
}
