package com.telesentinel.rag.client;

import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class IncidentClient {

    private final RestClient client;

    public IncidentClient(@Value("${telesentinel.services.correlation-url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        this.client = RestClient.builder().requestFactory(factory).baseUrl(baseUrl).build();
    }

    public Map<String, Object> get(UUID id) {
        return client.get().uri("/api/v1/incidents/{id}", id).retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() { });
    }
}
