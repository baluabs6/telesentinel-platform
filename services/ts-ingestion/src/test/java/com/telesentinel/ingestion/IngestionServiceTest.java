package com.telesentinel.ingestion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.telesentinel.ingestion.model.AlarmEvent;
import com.telesentinel.ingestion.model.CdrEvent;
import com.telesentinel.ingestion.repository.RawEventRepository;
import com.telesentinel.ingestion.service.IngestionService;
import com.telesentinel.ingestion.service.IngestionService.BatchResult;
import com.telesentinel.ingestion.service.IngestionService.Result;
import com.telesentinel.ingestion.service.PublishFailedException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    private static final String KEY = "dedupe:alarm:n:LINK_DOWN";

    @Mock KafkaTemplate<String, Object> kafka;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> ops;
    @Mock RawEventRepository raw;

    IngestionService service;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(ops);
        service = new IngestionService(kafka, redis, raw, "alarms", "cdrs", 60);
    }

    private AlarmEvent alarm(String severity, String status) {
        return new AlarmEvent("a1", "n", severity, "LINK_DOWN", "m", Instant.now(), status);
    }

    private CdrEvent cdr(String id) {
        return new CdrEvent(id, "+911", "+912", Instant.now(), 5, "LOCAL");
    }

    private void kafkaOk() {
        when(kafka.send(any(String.class), any(String.class), any()))
                .thenReturn(CompletableFuture.<SendResult<String, Object>>completedFuture(null));
    }

    @Test
    void escalationIsNotTreatedAsDuplicate() {
        when(ops.get(KEY)).thenReturn("MAJOR");
        kafkaOk();
        assertEquals(Result.ACCEPTED, service.ingestAlarm(alarm("CRITICAL", null)));
        verify(ops).set(eq(KEY), eq("CRITICAL"), any(Duration.class));
    }

    @Test
    void sameSeverityRepeatIsDuplicate() {
        when(ops.get(KEY)).thenReturn("CRITICAL");
        assertEquals(Result.DUPLICATE, service.ingestAlarm(alarm("CRITICAL", null)));
        verifyNoInteractions(kafka);
    }

    @Test
    void failedPublishReleasesDedupeKeySoRetryWorks() {
        when(ops.get(KEY)).thenReturn(null);
        when(kafka.send(any(String.class), any(String.class), any()))
                .thenReturn(CompletableFuture.<SendResult<String, Object>>failedFuture(new RuntimeException("broker down")));
        assertThrows(PublishFailedException.class, () -> service.ingestAlarm(alarm("MAJOR", null)));
        verify(redis).delete(KEY);
    }

    @Test
    void clearedAlarmResetsKeyAndIsPublished() {
        kafkaOk();
        assertEquals(Result.ACCEPTED, service.ingestAlarm(alarm("MAJOR", "CLEARED")));
        verify(redis).delete(KEY);
        verify(kafka).send(eq("alarms"), eq("n"), any());
    }

    @Test
    void duplicateCdrIsIgnored() {
        when(ops.setIfAbsent(eq("dedupe:cdr:c1"), any(), any(Duration.class))).thenReturn(false);
        assertEquals(Result.DUPLICATE, service.ingestCdr(cdr("c1")));
        verifyNoInteractions(kafka);
    }

    @Test
    void batchCountsAcceptedAndDuplicates() {
        when(ops.setIfAbsent(eq("dedupe:cdr:c1"), any(), any(Duration.class))).thenReturn(true);
        when(ops.setIfAbsent(eq("dedupe:cdr:c2"), any(), any(Duration.class))).thenReturn(false);
        kafkaOk();
        assertEquals(new BatchResult(1, 1), service.ingestCdrs(List.of(cdr("c1"), cdr("c2"))));
    }

    @Test
    void failedBatchReleasesKeysAndFails() {
        when(ops.setIfAbsent(eq("dedupe:cdr:c1"), any(), any(Duration.class))).thenReturn(true);
        when(kafka.send(any(String.class), any(String.class), any()))
                .thenReturn(CompletableFuture.<SendResult<String, Object>>failedFuture(new RuntimeException("down")));
        assertThrows(PublishFailedException.class, () -> service.ingestCdrs(List.of(cdr("c1"))));
        verify(redis).delete("dedupe:cdr:c1");
    }

    @Test
    void numbersAreCanonicalisedWithLeadingPlus() {
        when(ops.setIfAbsent(eq("dedupe:cdr:c9"), any(), any(Duration.class))).thenReturn(true);
        kafkaOk();
        service.ingestCdr(new CdrEvent("c9", "911", "882130001", Instant.now(), 5, "LOCAL"));
        verify(kafka).send(eq("cdrs"), eq("+911"), any());
    }
}
