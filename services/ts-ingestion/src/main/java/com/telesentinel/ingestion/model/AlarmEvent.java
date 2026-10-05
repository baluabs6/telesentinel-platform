package com.telesentinel.ingestion.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;

public record AlarmEvent(
        @NotBlank String alarmId,
        @NotBlank String nodeId,
        @NotBlank String severity,   // CRITICAL | MAJOR | MINOR | WARNING
        @NotBlank String type,       // e.g. LINK_DOWN, CELL_OUTAGE
        String message,
        @NotNull Instant raisedAt,
        @Pattern(regexp = "RAISED|CLEARED") String status) {   // null means RAISED
}
