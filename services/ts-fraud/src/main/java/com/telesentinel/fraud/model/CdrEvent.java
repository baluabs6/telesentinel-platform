package com.telesentinel.fraud.model;

import java.time.Instant;

public record CdrEvent(String cdrId, String callingNumber, String calledNumber,
                       Instant startTime, long durationSeconds, String callType) {
}
