package com.telesentinel.correlation.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Published to Kafka (telesentinel.incidents) for notification and the RAG assistant. */
public record IncidentEvent(UUID id, String rootNode, String rootAlarmType, String severity,
                            List<String> impactedNodes, int alarmCount, long estimatedSubscribers,
                            String summary, String status, Instant openedAt) {
}
