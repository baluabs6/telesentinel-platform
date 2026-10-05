package com.telesentinel.fraud.rules;

import com.telesentinel.fraud.model.FraudAlert;
import java.time.Duration;

/** A rule's finding plus how to suppress repeats. The engine, not the rule, applies suppression. */
public record RuleHit(FraudAlert alert, String suppressionKey, Duration suppressionTtl) {
}
