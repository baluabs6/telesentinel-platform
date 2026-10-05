package com.telesentinel.rag.web;

import com.telesentinel.rag.service.AssistantService;
import com.telesentinel.rag.service.KnowledgeService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;

@RestController
@RequestMapping("/api/v1")
public class AssistantController {

    private static final Logger log = LoggerFactory.getLogger(AssistantController.class);

    private final AssistantService assistant;
    private final KnowledgeService knowledge;

    public AssistantController(AssistantService assistant, KnowledgeService knowledge) {
        this.assistant = assistant;
        this.knowledge = knowledge;
    }

    @PostMapping("/assistant/ask")
    public AnswerResponse ask(@Valid @RequestBody AskRequest request) {
        return assistant.ask(request.question());
    }

    @PostMapping("/assistant/incidents/{id}/summary")
    public AnswerResponse incidentSummary(@PathVariable UUID id) {
        return assistant.summarizeIncident(id);
    }

    @PostMapping("/assistant/fraud/explain")
    public AnswerResponse explainFraud(@Valid @RequestBody FraudExplainRequest request) {
        return assistant.explainFraud(request);
    }

    @PostMapping("/knowledge")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addKnowledge(@Valid @RequestBody KnowledgeRequest request) {
        return Map.of("source", request.source(), "chunks", knowledge.ingest(request.source(), request.content()));
    }

    @ExceptionHandler(HttpClientErrorException.NotFound.class)
    ResponseEntity<Map<String, String>> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "incident not found"));
    }

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<Map<String, String>> upstreamFailure(RuntimeException e) {
        log.error("Assistant call failed", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "assistant temporarily unavailable"));
    }
}
