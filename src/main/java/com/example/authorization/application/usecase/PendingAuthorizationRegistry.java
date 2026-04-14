package com.example.authorization.application.usecase;

import com.example.authorization.domain.model.AuthorizationResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PendingAuthorizationRegistry {

    private final ConcurrentHashMap<String, CompletableFuture<AuthorizationResult>> pending =
            new ConcurrentHashMap<>();

    public CompletableFuture<AuthorizationResult> register(String transactionId) {
        CompletableFuture<AuthorizationResult> future = new CompletableFuture<>();
        pending.put(transactionId, future);
        return future;
    }

    public void complete(String transactionId, AuthorizationResult result) {
        CompletableFuture<AuthorizationResult> future = pending.remove(transactionId);
        if (future != null) {
            future.complete(result);
        }
    }

    public void remove(String transactionId) {
        pending.remove(transactionId);
    }
}
