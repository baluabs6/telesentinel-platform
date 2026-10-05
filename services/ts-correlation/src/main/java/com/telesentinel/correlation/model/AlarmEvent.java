package com.telesentinel.correlation.model;

import java.time.Instant;

/** status: RAISED (or null) or CLEARED. */
public record AlarmEvent(String alarmId, String nodeId, String severity, String type,
                         String message, Instant raisedAt, String status) {
}
