package com.telesentinel.rag.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record FraudExplainRequest(@NotBlank String ruleId, @NotBlank String subscriber,
                                  @NotBlank String severity, @Min(0) @Max(100) int score,
                                  @NotBlank String reason) { }
