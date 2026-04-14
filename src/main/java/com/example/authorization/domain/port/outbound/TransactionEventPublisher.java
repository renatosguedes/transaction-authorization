package com.example.authorization.domain.port.outbound;

import com.example.authorization.domain.model.Transaction;

public interface TransactionEventPublisher {
    void publish(Transaction transaction);
}
