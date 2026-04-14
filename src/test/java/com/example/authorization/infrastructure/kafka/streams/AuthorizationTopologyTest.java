package com.example.authorization.infrastructure.kafka.streams;

import com.example.authorization.domain.model.DenialReason;
import com.example.authorization.infrastructure.kafka.producer.TransactionEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationTopologyTest {

    static final String REQUESTS_TOPIC = "authorization-requests";
    static final String APPROVED_TOPIC = "authorization-approved";
    static final String DENIED_TOPIC   = "authorization-denied";
    static final String DLQ_TOPIC      = "authorization-dlq";

    TopologyTestDriver driver;
    TestInputTopic<String, String>  inputTopic;
    TestOutputTopic<String, String> approvedOutput;
    TestOutputTopic<String, String> deniedOutput;
    TestOutputTopic<String, String> dlqOutput;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        AuthorizationTopology topology = new AuthorizationTopology(
                REQUESTS_TOPIC, APPROVED_TOPIC, DENIED_TOPIC, DLQ_TOPIC, objectMapper);

        StreamsBuilder builder = new StreamsBuilder();
        AuthorizationTopology.addStateStore(builder);
        topology.buildTopology(builder);

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG,            "test-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG,         "dummy:9092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG,
                  org.apache.kafka.common.serialization.Serdes.StringSerde.class.getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG,
                  org.apache.kafka.common.serialization.Serdes.StringSerde.class.getName());

        driver = new TopologyTestDriver(builder.build(), props);

        inputTopic    = driver.createInputTopic(REQUESTS_TOPIC, new StringSerializer(), new StringSerializer());
        approvedOutput = driver.createOutputTopic(APPROVED_TOPIC, new StringDeserializer(), new StringDeserializer());
        deniedOutput   = driver.createOutputTopic(DENIED_TOPIC,   new StringDeserializer(), new StringDeserializer());
        dlqOutput      = driver.createOutputTopic(DLQ_TOPIC,      new StringDeserializer(), new StringDeserializer());
    }

    @AfterEach
    void tearDown() {
        driver.close();
    }

    // --- Happy path ---

    @Test
    void should_approve_first_transaction_for_account() throws Exception {
        pipe("txn-1", "acc-1", Instant.ofEpochMilli(1000));

        assertThat(approvedOutput.isEmpty()).isFalse();
        assertThat(deniedOutput.isEmpty()).isTrue();
        assertThat(dlqOutput.isEmpty()).isTrue();

        AuthorizationResultEvent result = parseResult(approvedOutput.readValue());
        assertThat(result.transactionId()).isEqualTo("txn-1");
        assertThat(result.accountId()).isEqualTo("acc-1");
        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(result.reason()).isNull();
    }

    @Test
    void should_approve_second_transaction_within_window() throws Exception {
        Instant base = Instant.ofEpochMilli(1000);
        pipe("txn-1", "acc-1", base);
        pipe("txn-2", "acc-1", base.plusMillis(1000));

        assertThat(approvedOutput.readValuesToList()).hasSize(2);
        assertThat(deniedOutput.isEmpty()).isTrue();
    }

    @Test
    void should_approve_third_transaction_at_limit_boundary() throws Exception {
        Instant base = Instant.ofEpochMilli(1000);
        pipe("txn-1", "acc-1", base);
        pipe("txn-2", "acc-1", base.plusMillis(1000));
        pipe("txn-3", "acc-1", base.plusMillis(2000));

        assertThat(approvedOutput.readValuesToList()).hasSize(3);
        assertThat(deniedOutput.isEmpty()).isTrue();
    }

    // --- Rate limit ---

    @Test
    void should_deny_fourth_transaction_when_rate_limit_exceeded() throws Exception {
        Instant base = Instant.ofEpochMilli(1000);
        pipe("txn-1", "acc-1", base);
        pipe("txn-2", "acc-1", base.plusMillis(1000));
        pipe("txn-3", "acc-1", base.plusMillis(2000));
        pipe("txn-4", "acc-1", base.plusMillis(3000));

        assertThat(approvedOutput.readValuesToList()).hasSize(3);
        assertThat(deniedOutput.isEmpty()).isFalse();

        AuthorizationResultEvent denied = parseResult(deniedOutput.readValue());
        assertThat(denied.transactionId()).isEqualTo("txn-4");
        assertThat(denied.status()).isEqualTo("DENIED");
        assertThat(denied.reason()).isEqualTo(DenialReason.RATE_LIMIT_EXCEEDED.getDescription());
    }

    @Test
    void should_approve_transaction_for_different_account_independently() throws Exception {
        Instant base = Instant.ofEpochMilli(1000);
        pipe("txn-1", "acc-A", base);
        pipe("txn-2", "acc-A", base.plusMillis(1000));
        pipe("txn-3", "acc-A", base.plusMillis(2000));
        pipe("txn-4", "acc-B", base.plusMillis(3000));

        assertThat(approvedOutput.readValuesToList()).hasSize(4);
        assertThat(deniedOutput.isEmpty()).isTrue();
    }

    @Test
    void should_approve_transaction_after_rate_limit_window_expires() throws Exception {
        Instant base = Instant.ofEpochMilli(1000);
        pipe("txn-1", "acc-1", base);
        pipe("txn-2", "acc-1", base.plusMillis(1000));
        pipe("txn-3", "acc-1", base.plusMillis(2000));

        // 4th event after the 60s window has moved past the first 3 events
        pipe("txn-4", "acc-1", base.plusSeconds(61));

        assertThat(approvedOutput.readValuesToList()).hasSize(4);
        assertThat(deniedOutput.isEmpty()).isTrue();
    }

    // --- DLQ routing ---

    @Test
    void should_route_null_value_to_dlq() {
        inputTopic.pipeInput("txn-null", null, Instant.ofEpochMilli(1000));

        assertThat(dlqOutput.isEmpty()).isFalse();
        assertThat(approvedOutput.isEmpty()).isTrue();
        assertThat(deniedOutput.isEmpty()).isTrue();
    }

    @Test
    void should_route_malformed_json_to_dlq() {
        inputTopic.pipeInput("txn-bad", "not-valid-json", Instant.ofEpochMilli(1000));

        assertThat(dlqOutput.isEmpty()).isFalse();
        assertThat(approvedOutput.isEmpty()).isTrue();
    }

    @Test
    void should_route_json_with_missing_required_fields_to_dlq() {
        inputTopic.pipeInput("txn-missing", "{\"foo\":\"bar\"}", Instant.ofEpochMilli(1000));

        assertThat(dlqOutput.isEmpty()).isFalse();
        assertThat(approvedOutput.isEmpty()).isTrue();
    }

    @Test
    void should_forward_original_raw_value_to_dlq() {
        String rawJson = "not-valid-json";
        inputTopic.pipeInput("txn-bad", rawJson, Instant.ofEpochMilli(1000));

        assertThat(dlqOutput.readValue()).isEqualTo(rawJson);
    }

    // --- Key and output structure ---

    @Test
    void should_preserve_transaction_id_as_kafka_record_key_in_approved_output() {
        pipe("txn-key", "acc-1", Instant.ofEpochMilli(1000));

        var record = approvedOutput.readKeyValue();
        assertThat(record.key).isEqualTo("txn-key");
    }

    @Test
    void should_preserve_transaction_id_as_kafka_record_key_in_denied_output() {
        Instant base = Instant.ofEpochMilli(1000);
        pipe("txn-1", "acc-1", base);
        pipe("txn-2", "acc-1", base.plusMillis(1000));
        pipe("txn-3", "acc-1", base.plusMillis(2000));
        pipe("txn-denied", "acc-1", base.plusMillis(3000));

        var record = deniedOutput.readKeyValue();
        assertThat(record.key).isEqualTo("txn-denied");
    }

    @Test
    void should_serialize_approved_result_with_all_required_fields() throws Exception {
        pipe("txn-1", "acc-1", Instant.ofEpochMilli(1000));

        AuthorizationResultEvent result = parseResult(approvedOutput.readValue());
        assertThat(result.transactionId()).isEqualTo("txn-1");
        assertThat(result.accountId()).isEqualTo("acc-1");
        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(result.reason()).isNull();
        assertThat(result.processedAt()).isNotNull();
    }

    @Test
    void should_serialize_denied_result_with_reason_field() throws Exception {
        Instant base = Instant.ofEpochMilli(1000);
        pipe("txn-1", "acc-1", base);
        pipe("txn-2", "acc-1", base.plusMillis(1000));
        pipe("txn-3", "acc-1", base.plusMillis(2000));
        pipe("txn-4", "acc-1", base.plusMillis(3000));

        AuthorizationResultEvent result = parseResult(deniedOutput.readValue());
        assertThat(result.status()).isEqualTo("DENIED");
        assertThat(result.reason()).isNotBlank();
        assertThat(result.processedAt()).isNotNull();
    }

    // --- Helpers ---

    private void pipe(String transactionId, String accountId, Instant eventTime) {
        try {
            TransactionEvent event = new TransactionEvent(
                    transactionId, accountId, "merchant-1",
                    new BigDecimal("100.00"), "BRL", eventTime);
            inputTopic.pipeInput(transactionId, objectMapper.writeValueAsString(event), eventTime);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private AuthorizationResultEvent parseResult(String json) throws Exception {
        return objectMapper.readValue(json, AuthorizationResultEvent.class);
    }
}
