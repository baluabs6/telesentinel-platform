package com.telesentinel.rag.service;

import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Retrieval is done explicitly (not hidden in an advisor) so we know the sources and can skip the LLM when nothing matches. */
@Service
public class RetrievalService {

    private final VectorStore vectorStore;
    private final int topK;
    private final double threshold;

    public RetrievalService(VectorStore vectorStore,
                            @Value("${telesentinel.rag.top-k:4}") int topK,
                            @Value("${telesentinel.rag.similarity-threshold:0.5}") double threshold) {
        this.vectorStore = vectorStore;
        this.topK = topK;
        this.threshold = threshold;
    }

    public List<ContextChunk> retrieve(String query) {
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK).similarityThreshold(threshold).build());
        if (docs == null) {
            return List.of();
        }
        return docs.stream()
                .map(d -> new ContextChunk(String.valueOf(d.getMetadata().getOrDefault("source", "unknown")), d.getText()))
                .toList();
    }
}
