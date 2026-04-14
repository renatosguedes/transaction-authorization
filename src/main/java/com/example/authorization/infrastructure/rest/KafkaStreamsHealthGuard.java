package com.example.authorization.infrastructure.rest;

import org.apache.kafka.streams.KafkaStreams;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.stereotype.Component;

@Component
public class KafkaStreamsHealthGuard {

    private final StreamsBuilderFactoryBean streamsFactory;

    public KafkaStreamsHealthGuard(StreamsBuilderFactoryBean streamsFactory) {
        this.streamsFactory = streamsFactory;
    }

    public boolean isReady() {
        KafkaStreams streams = streamsFactory.getKafkaStreams();
        return streams != null && streams.state() == KafkaStreams.State.RUNNING;
    }
}
