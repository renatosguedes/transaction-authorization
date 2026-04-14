package com.example.authorization.infrastructure.kafka.producer;

import com.example.authorization.domain.model.Money;
import com.example.authorization.domain.model.Transaction;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaTransactionEventPublisherTest {

    @Mock
    KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    KafkaTransactionEventPublisher publisher;

    static final String TOPIC = "authorization-requests";

    Transaction transaction;

    @BeforeEach
    void setUp() {
        publisher = new KafkaTransactionEventPublisher(kafkaTemplate, TOPIC);
        transaction = new Transaction(
                "txn-001",
                "acc-123",
                "merchant-456",
                Money.of(new BigDecimal("150.00"), "BRL"),
                Instant.parse("2026-01-01T10:00:00Z")
        );
    }

    @Test
    void should_publish_event_with_transaction_id_as_key() {
        stubSuccessfulSend();
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

        publisher.publish(transaction);

        verify(kafkaTemplate).send(any(), keyCaptor.capture(), any());
        assertThat(keyCaptor.getValue()).isEqualTo("txn-001");
    }

    @Test
    void should_send_to_correct_topic() {
        stubSuccessfulSend();
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);

        publisher.publish(transaction);

        verify(kafkaTemplate).send(topicCaptor.capture(), any(), any());
        assertThat(topicCaptor.getValue()).isEqualTo(TOPIC);
    }

    @Test
    void should_map_domain_transaction_to_transaction_event() {
        stubSuccessfulSend();
        ArgumentCaptor<TransactionEvent> eventCaptor = ArgumentCaptor.forClass(TransactionEvent.class);

        publisher.publish(transaction);

        verify(kafkaTemplate).send(any(), any(), eventCaptor.capture());
        TransactionEvent event = eventCaptor.getValue();
        assertThat(event.transactionId()).isEqualTo("txn-001");
        assertThat(event.accountId()).isEqualTo("acc-123");
        assertThat(event.merchantId()).isEqualTo("merchant-456");
        assertThat(event.amount()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(event.currency()).isEqualTo("BRL");
        assertThat(event.createdAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
    }

    @Test
    void should_not_throw_when_send_fails() {
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        assertThatNoException().isThrownBy(() -> publisher.publish(transaction));
    }

    // --- helpers ---

    private void stubSuccessfulSend() {
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition(TOPIC, 0), 0L, 0, 0L, 0, 0);
        TransactionEvent event = TransactionEvent.from(transaction);
        SendResult<String, TransactionEvent> sendResult = new SendResult<>(
                new ProducerRecord<>(TOPIC, transaction.transactionId(), event), metadata);
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));
    }
}
