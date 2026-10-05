package com.telesentinel.rag.config;

import com.telesentinel.rag.service.KnowledgeService;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/** Seeds the vector store with the sample runbooks the first time the service starts. */
@Component
public class KnowledgeLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeLoader.class);

    private final KnowledgeService knowledge;
    private final VectorStore vectorStore;
    private final boolean enabled;

    public KnowledgeLoader(KnowledgeService knowledge, VectorStore vectorStore,
                           @Value("${telesentinel.rag.load-samples:true}") boolean enabled) {
        this.knowledge = knowledge;
        this.vectorStore = vectorStore;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!enabled) {
            return;
        }
        try {
            boolean empty = vectorStore.similaritySearch(
                    SearchRequest.builder().query("runbook").topK(1).similarityThreshold(0.0).build()).isEmpty();
            if (!empty) {
                return;
            }
            for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath:knowledge/*.md")) {
                int chunks = knowledge.ingest(r.getFilename(), r.getContentAsString(StandardCharsets.UTF_8));
                log.info("Loaded {} ({} chunks)", r.getFilename(), chunks);
            }
        } catch (Exception e) {
            log.warn("Sample knowledge not loaded (is the embedding model configured?): {}", e.getMessage());
        }
    }
}
