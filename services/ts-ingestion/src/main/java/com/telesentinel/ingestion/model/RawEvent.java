package com.telesentinel.ingestion.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Raw payload kept in MongoDB for audit and replay. Expires automatically (retention policy). */
@Document("raw_events")
public record RawEvent(@Id String id, String kind, Object payload,
                       @Indexed(expireAfter = "${telesentinel.retention.raw-events:P30D}") Instant receivedAt) {
}
