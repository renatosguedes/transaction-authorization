package com.example.authorization.infrastructure.kafka.streams;

import com.example.authorization.domain.model.DenialReason;
import com.example.authorization.infrastructure.kafka.producer.TransactionEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.processor.api.FixedKeyProcessor;
import org.apache.kafka.streams.processor.api.FixedKeyProcessorContext;
import org.apache.kafka.streams.processor.api.FixedKeyRecord;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.WindowStore;
import org.apache.kafka.streams.state.WindowStoreIterator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Configuration
@EnableKafkaStreams
public class AuthorizationTopology {

    static final String STORE_NAME       = "rate-limit-store";
    static final long   WINDOW_SIZE_MS   = 60_000L;
    static final int    MAX_TRANSACTIONS = 3;

    private final String requestsTopic;
    private final String approvedTopic;
    private final String deniedTopic;
    private final String dlqTopic;
    private final ObjectMapper objectMapper;

    public AuthorizationTopology(
            @Value("${kafka.topics.requests}") String requestsTopic,
            @Value("${kafka.topics.approved}")  String approvedTopic,
            @Value("${kafka.topics.denied}")    String deniedTopic,
            @Value("${kafka.topics.dlq}")       String dlqTopic,
            ObjectMapper objectMapper) {
        this.requestsTopic = requestsTopic;
        this.approvedTopic = approvedTopic;
        this.deniedTopic   = deniedTopic;
        this.dlqTopic      = dlqTopic;
        this.objectMapper  = objectMapper;
    }

    @Bean
    public KStream<String, String> authorizationStream(StreamsBuilder builder) {
        addStateStore(builder);
        return buildTopology(builder);
    }

    static void addStateStore(StreamsBuilder builder) {
        StoreBuilder<WindowStore<String, Long>> storeBuilder =
                Stores.windowStoreBuilder(
                        Stores.inMemoryWindowStore(
                                STORE_NAME,
                                Duration.ofMillis(WINDOW_SIZE_MS * 2),
                                Duration.ofMillis(WINDOW_SIZE_MS),
                                true   // retainDuplicates: each event gets its own slot
                        ),
                        Serdes.String(),
                        Serdes.Long()
                );
        builder.addStateStore(storeBuilder);
    }

    KStream<String, String> buildTopology(StreamsBuilder builder) {
        KStream<String, String> source = builder.stream(
                requestsTopic, Consumed.with(Serdes.String(), Serdes.String()));

        // --- parse stage ---
        KStream<String, ParseResult> parsed = source.mapValues(this::deserialize);

        Map<String, KStream<String, ParseResult>> parsedBranches = parsed
                .split(Named.as("parse-"))
                .branch((k, v) -> v instanceof ParseResult.Failure, Branched.as("failure"))
                .defaultBranch(Branched.as("success"));

        // --- DLQ ---
        parsedBranches.get("parse-failure")
                .mapValues(v -> ((ParseResult.Failure) v).rawValue())
                .to(dlqTopic, Produced.with(Serdes.String(), Serdes.String()));

        // --- rate-limit processing ---
        KStream<String, AuthorizationResultEvent> results = parsedBranches
                .get("parse-success")
                .mapValues(v -> ((ParseResult.Success) v).event())
                .processValues(RateLimitProcessor::new, STORE_NAME);

        // --- output routing ---
        Map<String, KStream<String, AuthorizationResultEvent>> outputBranches = results
                .split(Named.as("result-"))
                .branch((k, v) -> "APPROVED".equals(v.status()), Branched.as("approved"))
                .branch((k, v) -> "DENIED".equals(v.status()),   Branched.as("denied"))
                .noDefaultBranch();

        outputBranches.get("result-approved")
                .mapValues(this::serializeToJson)
                .to(approvedTopic, Produced.with(Serdes.String(), Serdes.String()));

        outputBranches.get("result-denied")
                .mapValues(this::serializeToJson)
                .to(deniedTopic, Produced.with(Serdes.String(), Serdes.String()));

        return source;
    }

    private ParseResult deserialize(String raw) {
        if (raw == null) {
            return new ParseResult.Failure(null);
        }
        try {
            TransactionEvent event = objectMapper.readValue(raw, TransactionEvent.class);
            return new ParseResult.Success(event);
        } catch (JsonProcessingException e) {
            return new ParseResult.Failure(raw);
        }
    }

    private String serializeToJson(AuthorizationResultEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize AuthorizationResultEvent", e);
        }
    }

    // --- Internal sealed type for parse results ---

    sealed interface ParseResult {
        record Success(TransactionEvent event) implements ParseResult {}
        record Failure(String rawValue)        implements ParseResult {}
    }

    // --- Stateful rate-limit processor ---

    private static final class RateLimitProcessor
            implements FixedKeyProcessor<String, TransactionEvent, AuthorizationResultEvent> {

        private FixedKeyProcessorContext<String, AuthorizationResultEvent> context;
        private WindowStore<String, Long> store;

        @Override
        public void init(FixedKeyProcessorContext<String, AuthorizationResultEvent> context) {
            this.context = context;
            this.store = context.getStateStore(STORE_NAME);
        }

        @Override
        public void process(FixedKeyRecord<String, TransactionEvent> record) {
            TransactionEvent event      = record.value();
            String           accountId  = event.accountId();
            long             eventTimeMs = record.timestamp();
            long             windowStart = eventTimeMs - WINDOW_SIZE_MS;

            long count = countInWindow(accountId, windowStart, eventTimeMs);

            AuthorizationResultEvent result;
            if (count >= MAX_TRANSACTIONS) {
                result = new AuthorizationResultEvent(
                        event.transactionId(),
                        accountId,
                        "DENIED",
                        DenialReason.RATE_LIMIT_EXCEEDED.getDescription(),
                        Instant.ofEpochMilli(eventTimeMs)
                );
            } else {
                store.put(accountId, 1L, eventTimeMs);
                result = new AuthorizationResultEvent(
                        event.transactionId(),
                        accountId,
                        "APPROVED",
                        null,
                        Instant.ofEpochMilli(eventTimeMs)
                );
            }
            context.forward(record.withValue(result));
        }

        @Override
        public void close() {}

        private long countInWindow(String accountId, long from, long to) {
            long count = 0;
            try (WindowStoreIterator<Long> it = store.fetch(accountId, from, to)) {
                while (it.hasNext()) {
                    it.next();
                    count++;
                }
            }
            return count;
        }
    }
}
