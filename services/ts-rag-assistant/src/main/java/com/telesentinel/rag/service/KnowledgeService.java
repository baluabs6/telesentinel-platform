package com.telesentinel.rag.service;

import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeService {

    private final VectorStore vectorStore;
    private final TokenTextSplitter splitter = new TokenTextSplitter();

    public KnowledgeService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /** Splits text into chunks, embeds them, and stores them. Returns the chunk count. */
    public int ingest(String source, String content) {
        Document doc = new Document(content, Map.<String, Object>of("source", source));
        List<Document> chunks = splitter.apply(List.of(doc));
        vectorStore.add(chunks);
        return chunks.size();
    }
}
