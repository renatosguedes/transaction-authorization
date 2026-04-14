package com.example.authorization.infrastructure.persistence;

import com.example.authorization.domain.model.AuthorizationResult;
import com.example.authorization.domain.port.outbound.AuthorizationResultRepository;
import com.example.authorization.infrastructure.persistence.entity.AuthorizationResultEntity;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class PostgresAuthorizationResultRepository implements AuthorizationResultRepository {

    private final SpringDataAuthorizationResultRepository jpaRepo;

    public PostgresAuthorizationResultRepository(SpringDataAuthorizationResultRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @Override
    public void save(AuthorizationResult result) {
        jpaRepo.save(toEntity(result));
    }

    @Override
    public Optional<AuthorizationResult> findByTransactionId(String transactionId) {
        return jpaRepo.findByTransactionId(transactionId).map(this::toDomain);
    }

    private AuthorizationResultEntity toEntity(AuthorizationResult result) {
        return new AuthorizationResultEntity(
                result.transactionId(),
                result.accountId(),
                result.status().name(),
                result.reason(),
                result.processedAt()
        );
    }

    private AuthorizationResult toDomain(AuthorizationResultEntity entity) {
        return new AuthorizationResult(
                entity.getTransactionId(),
                entity.getAccountId(),
                AuthorizationResult.Status.valueOf(entity.getStatus()),
                entity.getReason(),
                entity.getProcessedAt()
        );
    }
}
