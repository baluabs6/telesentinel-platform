package com.telesentinel.fraud.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.telesentinel.fraud.config.FraudProperties;
import com.telesentinel.fraud.model.CdrEvent;
import com.telesentinel.fraud.persistence.FraudAlertEntity;
import com.telesentinel.fraud.persistence.FraudAlertRepository;
import com.telesentinel.fraud.rules.IrsfRule;
import com.telesentinel.fraud.rules.WangiriRule;
import com.telesentinel.fraud.store.InMemoryVelocityStore;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class FraudEngineTest {

    private FraudAlertRepository repo;
    private KafkaTemplate<String, Object> kafka;
    private InMemoryVelocityStore store;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        repo = mock(FraudAlertRepository.class);
        kafka = mock(KafkaTemplate.class);
        store = new InMemoryVelocityStore();
        when(repo.save(any(FraudAlertEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(kafka.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.<SendResult<String, Object>>completedFuture(null));
    }

    private FraudEngine engine(List<String> allowList) {
        FraudProperties props = new FraudProperties(
                new FraudProperties.Wangiri(6, 3, 300),
                new FraudProperties.Irsf(List.of("+882"), 600, 3600),
                allowList);
        return new FraudEngine(List.of(new WangiriRule(store, props), new IrsfRule(store, props)),
                repo, kafka, store, props, "alerts");
    }

    private CdrEvent cdr(String id, String from, String to, long secs) {
        return new CdrEvent(id, from, to, Instant.now(), secs, "INTERNATIONAL");
    }

    @Test
    void repeatedHitsInOneWindowProduceOneAlert() {
        FraudEngine engine = engine(List.of());
        for (int i = 0; i < 8; i++) {
            engine.onCdr(cdr("c" + i, "+911", "+9198000000" + i, 2));
        }
        verify(repo, times(1)).save(any(FraudAlertEntity.class));
    }

    @Test
    void redeliveredCdrIsNotCountedTwice() {
        FraudEngine engine = engine(List.of());
        CdrEvent c = cdr("d1", "+912", "+882130001", 400);   // 400 s < 600 s limit
        engine.onCdr(c);
        engine.onCdr(c);                                       // redelivery: would be 800 s if counted again
        verify(repo, never()).save(any(FraudAlertEntity.class));
    }

    @Test
    void allowListedSenderIsSkipped() {
        FraudEngine engine = engine(List.of("+912"));
        engine.onCdr(cdr("d1", "+912", "+882130001", 5000));
        verify(repo, never()).save(any(FraudAlertEntity.class));
    }

    @Test
    void storeFailureReleasesKeysSoTheRetryStillAlerts() {
        when(repo.save(any(FraudAlertEntity.class)))
                .thenThrow(new RuntimeException("db down"))
                .thenAnswer(i -> i.getArgument(0));
        FraudEngine engine = engine(List.of());
        CdrEvent c = cdr("d1", "+912", "+882130001", 700);    // above the limit on its own

        assertThrows(RuntimeException.class, () -> engine.onCdr(c));
        engine.onCdr(c);                                       // Kafka retry

        verify(repo, times(2)).save(any(FraudAlertEntity.class));
    }

    @Test
    void allowListMatchesRegardlessOfLeadingPlus() {
        FraudEngine engine = engine(List.of("912"));          // configured without '+'
        engine.onCdr(cdr("d1", "+912", "+882130001", 5000));   // arrives canonical, with '+'
        verify(repo, never()).save(any(FraudAlertEntity.class));
    }
}
