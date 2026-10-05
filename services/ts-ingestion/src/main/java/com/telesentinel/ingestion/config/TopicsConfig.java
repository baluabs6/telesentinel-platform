package com.telesentinel.ingestion.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Disabled where topics are managed elsewhere (Event Hubs via Terraform, Strimzi manifests). */
@Configuration
@ConditionalOnProperty(name = "telesentinel.kafka.create-topics", havingValue = "true", matchIfMissing = true)
public class TopicsConfig {

    @Bean
    NewTopic alarmsTopic(@Value("${telesentinel.topics.alarms}") String name,
                         @Value("${telesentinel.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(name).partitions(6).replicas(replicas).build();
    }

    @Bean
    NewTopic cdrsTopic(@Value("${telesentinel.topics.cdrs}") String name,
                       @Value("${telesentinel.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(name).partitions(12).replicas(replicas).build();
    }
}
