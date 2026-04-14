package com.example.authorization.infrastructure.kafka.producer;

import com.example.authorization.domain.model.Transaction;
import com.example.authorization.domain.port.outbound.TransactionEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaTransactionEventPublisher implements TransactionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaTransactionEventPublisher.class);

    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;
    private final String requestsTopic;

    public KafkaTransactionEventPublisher(
            KafkaTemplate<String, TransactionEvent> kafkaTemplate,
            @Value("${kafka.topics.requests}") String requestsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.requestsTopic = requestsTopic;
    }

    @Override
    public void publish(Transaction transaction) {
        TransactionEvent event = TransactionEvent.from(transaction);
        kafkaTemplate.send(requestsTopic, transaction.transactionId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish transaction event [transactionId={}]: {}",
                                transaction.transactionId(), ex.getMessage(), ex);
                        // TODO: route to DLQ or trigger retry policy
                    } else {
                        log.debug("Transaction event published [transactionId={}, partition={}, offset={}]",
                                transaction.transactionId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
