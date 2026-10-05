package com.telesentinel.ingestion.service;

/** Kafka did not acknowledge the event in time. The caller should retry; dedupe state was rolled back. */
public class PublishFailedException extends RuntimeException {
    public PublishFailedException(Throwable cause) {
        super("Could not publish event", cause);
    }
}
