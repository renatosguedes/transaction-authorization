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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class AuthorizeTransactionService implements AuthorizeTransactionUseCase {

    static final long RESULT_TIMEOUT_SECONDS = 5;

    private final TransactionEventPublisher eventPublisher;
    private final PendingAuthorizationRegistry registry;
    private final List<AuthorizationPolicy> policies;

    public AuthorizeTransactionService(TransactionEventPublisher eventPublisher,
                                       PendingAuthorizationRegistry registry) {
        this.eventPublisher = eventPublisher;
        this.registry = registry;
        this.policies = List.of(new AmountValidationPolicy());
    }

    @Override
    public AuthorizationResult authorize(Transaction transaction) {
        Optional<AuthorizationResult> denial = policies.stream()
                .map(policy -> policy.evaluate(transaction))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();

        if (denial.isPresent()) {
            return denial.get();
        }

        // Register future before publishing to avoid a race with a very fast consumer
        CompletableFuture<AuthorizationResult> future = registry.register(transaction.transactionId());
        eventPublisher.publish(transaction);

        try {
            return future.get(RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            registry.remove(transaction.transactionId());
            return AuthorizationResult.denied(transaction, "Authorization timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            registry.remove(transaction.transactionId());
            return AuthorizationResult.denied(transaction, "Authorization interrupted");
        } catch (ExecutionException e) {
            registry.remove(transaction.transactionId());
            return AuthorizationResult.denied(transaction, "Authorization error");
        }
    }
}
