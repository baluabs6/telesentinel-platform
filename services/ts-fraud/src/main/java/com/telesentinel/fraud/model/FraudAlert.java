package com.telesentinel.fraud.model;

import java.time.Instant;

public record FraudAlert(String ruleId, String subscriber, String severity, int score,
                         String reason, String cdrId, Instant createdAt) {
}
