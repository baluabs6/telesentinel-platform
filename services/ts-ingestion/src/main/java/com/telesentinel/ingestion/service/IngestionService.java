package com.telesentinel.ingestion.service;

import com.telesentinel.ingestion.model.AlarmEvent;
import com.telesentinel.ingestion.model.CdrEvent;
import com.telesentinel.ingestion.model.RawEvent;
import com.telesentinel.ingestion.repository.RawEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Ordering rule for every event: claim the dedupe key, publish and WAIT for the broker ack, then
 * audit. If publishing fails the key is released so the client's retry is not mistaken for a duplicate.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    private static final long SEND_TIMEOUT_MS = 5000;
    private static final Duration CDR_DEDUPE_TTL = Duration.ofMinutes(10);

    public enum Result { ACCEPTED, DUPLICATE }

    public record BatchResult(int accepted, int duplicates) { }

    private record Pending(String key, CdrEvent cdr, CompletableFuture<?> future) { }

    private final KafkaTemplate<String, Object> kafka;
    private final StringRedisTemplate redis;
    private final RawEventRepository rawEvents;
    private final String alarmsTopic;
    private final String cdrsTopic;
    private final Duration dedupeTtl;

    public IngestionService(KafkaTemplate<String, Object> kafka,
                            StringRedisTemplate redis,
                            RawEventRepository rawEvents,
                            @Value("${telesentinel.topics.alarms}") String alarmsTopic,
                            @Value("${telesentinel.topics.cdrs}") String cdrsTopic,
                            @Value("${telesentinel.dedupe-ttl-seconds}") long ttlSeconds) {
        this.kafka = kafka;
        this.redis = redis;
        this.rawEvents = rawEvents;
        this.alarmsTopic = alarmsTopic;
        this.cdrsTopic = cdrsTopic;
        this.dedupeTtl = Duration.ofSeconds(ttlSeconds);
    }

    /**
     * Repeats of the same (node, type, severity) inside the TTL are dropped. A change of severity
     * (an escalation) is NOT a duplicate. A CLEARED alarm always passes and resets the key.
     */
    public Result ingestAlarm(AlarmEvent alarm) {
        String key = "dedupe:alarm:" + alarm.nodeId() + ":" + alarm.type();

        if ("CLEARED".equalsIgnoreCase(alarm.status())) {
            redis.delete(key);
            await(kafka.send(alarmsTopic, alarm.nodeId(), alarm));
            audit("ALARM", alarm);
            return Result.ACCEPTED;
        }

        String previous = redis.opsForValue().get(key);
        String severity = alarm.severity().toUpperCase();
        if (severity.equals(previous)) {
            return Result.DUPLICATE;
        }
        redis.opsForValue().set(key, severity, dedupeTtl);
        try {
            // keyed by nodeId so one node's alarms stay ordered on one partition
            await(kafka.send(alarmsTopic, alarm.nodeId(), alarm));
        } catch (PublishFailedException e) {
            if (previous == null) {
                redis.delete(key);
            } else {
                redis.opsForValue().set(key, previous, dedupeTtl);
            }
            throw e;
        }
        audit("ALARM", alarm);
        return Result.ACCEPTED;
    }

    public Result ingestCdr(CdrEvent raw) {
        CdrEvent cdr = canonical(raw);
        String key = "dedupe:cdr:" + cdr.cdrId();
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "1", CDR_DEDUPE_TTL))) {
            return Result.DUPLICATE;
        }
        try {
            // keyed by calling number so the fraud service sees a subscriber's calls in order
            await(kafka.send(cdrsTopic, cdr.callingNumber(), cdr));
        } catch (PublishFailedException e) {
            redis.delete(key);
            throw e;
        }
        audit("CDR", cdr);
        return Result.ACCEPTED;
    }

    /** Sends all CDRs concurrently, then waits. On any failure the failed keys are released and the client retries the batch. */
    public BatchResult ingestCdrs(List<CdrEvent> cdrs) {
        List<Pending> pending = new ArrayList<>();
        int duplicates = 0;
        for (CdrEvent raw : cdrs) {
            CdrEvent cdr = canonical(raw);
            String key = "dedupe:cdr:" + cdr.cdrId();
            if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "1", CDR_DEDUPE_TTL))) {
                duplicates++;
                continue;
            }
            pending.add(new Pending(key, cdr, kafka.send(cdrsTopic, cdr.callingNumber(), cdr)));
        }

        int accepted = 0;
        PublishFailedException failure = null;
        for (Pending p : pending) {
            try {
                await(p.future());
                accepted++;
                audit("CDR", p.cdr());
            } catch (PublishFailedException e) {
                redis.delete(p.key());
                failure = e;
            }
        }
        if (failure != null) {
            throw failure;
        }
        return new BatchResult(accepted, duplicates);
    }

    /**
     * One spelling per subscriber: numbers always carry a leading '+', so counters, allow lists and
     * risky-prefix checks cannot be split by '+9198...' versus '9198...'. Senders are expected to use E.164.
     */
    private static CdrEvent canonical(CdrEvent c) {
        return new CdrEvent(c.cdrId(), plus(c.callingNumber()), plus(c.calledNumber()),
                c.startTime(), c.durationSeconds(), c.callType());
    }

    private static String plus(String number) {
        return number.startsWith("+") ? number : "+" + number;
    }

    private void await(CompletableFuture<?> future) {
        try {
            future.get(SEND_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PublishFailedException(e);
        } catch (ExecutionException | TimeoutException e) {
            throw new PublishFailedException(e);
        }
    }

    /** Audit is best effort: losing an audit copy must never fail ingestion of a published event. */
    private void audit(String kind, Object payload) {
        try {
            rawEvents.save(new RawEvent(null, kind, payload, Instant.now()));
        } catch (RuntimeException e) {
            log.warn("Audit write failed for {}: {}", kind, e.getMessage());
        }
    }
}
