package com.example.authorization.infrastructure.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    private final String requestsTopic;
    private final String approvedTopic;
    private final String deniedTopic;
    private final String dlqTopic;

    public KafkaTopicConfig(
            @Value("${kafka.topics.requests}") String requestsTopic,
            @Value("${kafka.topics.approved}")  String approvedTopic,
            @Value("${kafka.topics.denied}")    String deniedTopic,
            @Value("${kafka.topics.dlq}")       String dlqTopic) {
        this.requestsTopic = requestsTopic;
        this.approvedTopic = approvedTopic;
        this.deniedTopic   = deniedTopic;
        this.dlqTopic      = dlqTopic;
    }

    @Bean
    public NewTopic authorizationRequestsTopic() {
        return TopicBuilder.name(requestsTopic).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic authorizationApprovedTopic() {
        return TopicBuilder.name(approvedTopic).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic authorizationDeniedTopic() {
        return TopicBuilder.name(deniedTopic).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic authorizationDlqTopic() {
        return TopicBuilder.name(dlqTopic).partitions(1).replicas(1).build();
    }
}
