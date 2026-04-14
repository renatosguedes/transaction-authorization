package com.example.authorization.infrastructure.persistence;

import com.example.authorization.infrastructure.persistence.entity.AuthorizationResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface SpringDataAuthorizationResultRepository extends JpaRepository<AuthorizationResultEntity, Long> {
    Optional<AuthorizationResultEntity> findByTransactionId(String transactionId);
}
