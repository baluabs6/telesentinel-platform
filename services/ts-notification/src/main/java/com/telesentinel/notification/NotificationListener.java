package com.telesentinel.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private final ObjectMapper mapper;
    private final Notifier notifier;

    public NotificationListener(ObjectMapper mapper, Notifier notifier) {
        this.mapper = mapper;
        this.notifier = notifier;
    }

    @KafkaListener(topics = "${telesentinel.topics.fraud-alerts}")
    public void onFraudAlert(String payload) {
        handle(payload, true);
    }

    @KafkaListener(topics = "${telesentinel.topics.incidents}")
    public void onIncident(String payload) {
        handle(payload, false);
    }

    private void handle(String payload, boolean fraud) {
        try {
            JsonNode node = mapper.readTree(payload);
            notifier.send(fraud ? MessageFormatter.fraud(node) : MessageFormatter.incident(node));
        } catch (Exception e) {
            log.error("Skipping unreadable event: {}", e.getMessage());
        }
    }
}
