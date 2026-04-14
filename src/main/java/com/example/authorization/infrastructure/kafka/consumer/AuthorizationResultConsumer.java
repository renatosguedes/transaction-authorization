package com.example.authorization.infrastructure.kafka.consumer;

import com.example.authorization.application.usecase.PendingAuthorizationRegistry;
import com.example.authorization.domain.model.AuthorizationResult;
import com.example.authorization.domain.port.outbound.AuthorizationResultRepository;
import com.example.authorization.infrastructure.kafka.streams.AuthorizationResultEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AuthorizationResultConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationResultConsumer.class);

    private final PendingAuthorizationRegistry registry;
    private final AuthorizationResultRepository repository;
    private final ObjectMapper objectMapper;

    public AuthorizationResultConsumer(PendingAuthorizationRegistry registry,
                                       AuthorizationResultRepository repository,
                                       ObjectMapper objectMapper) {
        this.registry = registry;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {"${kafka.topics.approved}", "${kafka.topics.denied}"},
            containerFactory = "authorizationResultContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record) {
        try {
            AuthorizationResultEvent event = objectMapper.readValue(record.value(), AuthorizationResultEvent.class);
            AuthorizationResult result = new AuthorizationResult(
                    event.transactionId(),
                    event.accountId(),
                    AuthorizationResult.Status.valueOf(event.status()),
                    event.reason(),
                    event.processedAt()
            );
            registry.complete(event.transactionId(), result);
            repository.save(result);
        } catch (Exception e) {
            log.error("Failed to process authorization result from topic [{}] key [{}]: {}",
                    record.topic(), record.key(), e.getMessage());
        }
    }
}
