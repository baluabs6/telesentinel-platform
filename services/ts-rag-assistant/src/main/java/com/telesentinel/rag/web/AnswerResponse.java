package com.telesentinel.rag.web;

import java.util.List;

public record AnswerResponse(String answer, List<String> sources) {
}
