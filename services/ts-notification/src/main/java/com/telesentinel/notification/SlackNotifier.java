package com.telesentinel.notification;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class SlackNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(SlackNotifier.class);

    private final String webhookUrl;
    private final RestClient client;

    public SlackNotifier(@Value("${telesentinel.notify.slack-webhook-url:}") String webhookUrl) {
        this.webhookUrl = webhookUrl;
        // timeouts so a slow Slack can never stall the Kafka consumer thread
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public void send(String message) {
        log.info("NOTIFY {}", message.replace('\n', ' '));
        if (webhookUrl.isBlank()) {
            return;
        }
        try {
            client.post().uri(webhookUrl).body(Map.of("text", message)).retrieve().toBodilessEntity();
        } catch (Exception e) {
            // never let a chat outage block the consumer; the event is still logged above
            log.error("Slack delivery failed: {}", e.getMessage());
        }
    }
}
