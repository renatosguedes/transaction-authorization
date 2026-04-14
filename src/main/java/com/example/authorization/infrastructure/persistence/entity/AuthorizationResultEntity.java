package com.example.authorization.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "authorization_results")
public class AuthorizationResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, unique = true)
    private String transactionId;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(nullable = false)
    private String status;

    @Column
    private String reason;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public AuthorizationResultEntity() {
    }

    public AuthorizationResultEntity(String transactionId,
                                     String accountId,
                                     String status,
                                     String reason,
                                     Instant processedAt) {
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.status = status;
        this.reason = reason;
        this.processedAt = processedAt;
    }

    public Long getId() {
        return id;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
