package com.telesentinel.ingestion.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;

public record CdrEvent(
        @NotBlank String cdrId,
        @NotBlank @Pattern(regexp = "^\\+?[0-9]{6,15}$") String callingNumber,
        @NotBlank @Pattern(regexp = "^\\+?[0-9]{6,15}$") String calledNumber,
        @NotNull Instant startTime,
        @Min(0) long durationSeconds,
        @NotBlank String callType) {   // LOCAL | INTERNATIONAL | PREMIUM
}
