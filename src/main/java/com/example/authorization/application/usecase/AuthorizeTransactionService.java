package com.example.authorization.application.usecase;

import com.example.authorization.domain.model.AuthorizationResult;
import com.example.authorization.domain.model.Transaction;
import com.example.authorization.domain.policy.AmountValidationPolicy;
import com.example.authorization.domain.policy.AuthorizationPolicy;
import com.example.authorization.domain.port.inbound.AuthorizeTransactionUseCase;
import com.example.authorization.domain.port.outbound.TransactionEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AuthorizeTransactionService implements AuthorizeTransactionUseCase {

    private final TransactionEventPublisher eventPublisher;
    private final List<AuthorizationPolicy> policies;

    public AuthorizeTransactionService(TransactionEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        // Políticas stateless — a stateful (rate limit) roda no Kafka Streams
        this.policies = List.of(new AmountValidationPolicy());
    }

    @Override
    public AuthorizationResult authorize(Transaction transaction) {
        // Avalia todas as políticas stateless
        Optional<AuthorizationResult> denial = policies.stream()
                .map(policy -> policy.evaluate(transaction))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();

        if (denial.isPresent()) {
            return denial.get();
        }

        // Passou nas validações stateless: publica no Kafka para processamento stateful
        eventPublisher.publish(transaction);

        // Retorna PENDING — o resultado final vem do Kafka Streams
        return AuthorizationResult.approved(transaction); // simplificado para o MVP
    }
}
