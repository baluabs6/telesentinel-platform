package com.telesentinel.ingestion.controller;

import com.telesentinel.ingestion.service.PublishFailedException;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(PublishFailedException.class)
    ResponseEntity<Map<String, String>> publishFailed(PublishFailedException e) {
        log.error("Kafka publish failed", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "2")
                .body(Map.of("error", "event not accepted, please retry"));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Map<String, String>> invalid(ConstraintViolationException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid request: " + e.getMessage()));
    }
}
