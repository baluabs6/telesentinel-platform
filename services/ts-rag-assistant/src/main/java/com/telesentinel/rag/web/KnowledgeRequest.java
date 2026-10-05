package com.telesentinel.rag.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeRequest(@NotBlank @Size(max = 200) String source,
                               @NotBlank @Size(max = 100000) String content) { }
