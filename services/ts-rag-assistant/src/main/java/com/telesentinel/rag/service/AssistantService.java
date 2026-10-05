package com.telesentinel.rag.service;

import com.telesentinel.rag.client.IncidentClient;
import com.telesentinel.rag.web.AnswerResponse;
import com.telesentinel.rag.web.FraudExplainRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class AssistantService {

    static final String NO_CONTEXT = "No runbook or playbook in the knowledge base matches this. "
            + "Add one with POST /api/v1/knowledge and ask again.";

    private final ChatClient chat;
    private final RetrievalService retrieval;
    private final IncidentClient incidents;

    public AssistantService(ChatClient chat, RetrievalService retrieval, IncidentClient incidents) {
        this.chat = chat;
        this.retrieval = retrieval;
        this.incidents = incidents;
    }

    public AnswerResponse ask(String question) {
        return answer(question, PromptFactory.question(question), NO_CONTEXT);
    }

    public AnswerResponse summarizeIncident(UUID id) {
        Map<String, Object> incident = incidents.get(id);
        String query = PromptFactory.clean(incident.get("rootAlarmType")) + " incident troubleshooting runbook";
        String fallback = "No matching runbook. Facts only: " + PromptFactory.clean(incident.get("summary"));
        return answer(query, PromptFactory.incident(incident), fallback);
    }

    public AnswerResponse explainFraud(FraudExplainRequest request) {
        String query = PromptFactory.clean(request.ruleId()) + " fraud playbook";
        String fallback = "No matching playbook. Detector reason: " + PromptFactory.clean(request.reason());
        return answer(query, PromptFactory.fraud(request), fallback);
    }

    /** If nothing relevant is retrieved the model is not called: cheaper, and it cannot make things up. */
    private AnswerResponse answer(String retrievalQuery, String task, String fallback) {
        List<ContextChunk> chunks = retrieval.retrieve(retrievalQuery);
        if (chunks.isEmpty()) {
            return new AnswerResponse(fallback, List.of());
        }
        String text = chat.prompt().user(PromptFactory.withContext(task, chunks)).call().content();
        List<String> sources = chunks.stream().map(ContextChunk::source).distinct().toList();
        return new AnswerResponse(text, sources);
    }
}
