package com.telesentinel.correlation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.telesentinel.correlation.config.CorrelationProperties;
import com.telesentinel.correlation.model.AlarmEvent;
import com.telesentinel.correlation.persistence.ActiveAlarmEntity;
import com.telesentinel.correlation.persistence.ActiveAlarmRepository;
import com.telesentinel.correlation.service.ActiveAlarmStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ActiveAlarmStoreTest {

    private final Map<String, ActiveAlarmEntity> db = new LinkedHashMap<>();
    private ActiveAlarmRepository repo;

    @BeforeEach
    void setUp() {
        repo = mock(ActiveAlarmRepository.class);
        when(repo.save(any(ActiveAlarmEntity.class))).thenAnswer(i -> {
            ActiveAlarmEntity e = i.getArgument(0);
            db.put(e.getKey(), e);
            return e;
        });
        doAnswer(i -> {
            db.remove((String) i.getArgument(0));
            return null;
        }).when(repo).deleteById(any(String.class));
        when(repo.findAll()).thenAnswer(i -> new ArrayList<>(db.values()));
        when(repo.purgeOlderThan(any(Instant.class))).thenAnswer(i -> {
            Instant cutoff = i.getArgument(0);
            int before = db.size();
            db.values().removeIf(e -> e.getLastSeen().isBefore(cutoff));
            return before - db.size();
        });
    }

    private ActiveAlarmStore store(long ttlSeconds) {
        return new ActiveAlarmStore(repo, new CorrelationProperties(ttlSeconds, 30, 10_000,
                new CorrelationProperties.Topology(Map.of())));
    }

    private AlarmEvent alarm(String node, String type, String sev, String status) {
        return new AlarmEvent(node + type, node, sev, type, "", Instant.now(), status);
    }

    @Test
    void alarmStaysActiveUntilCleared() {
        ActiveAlarmStore store = store(3600);
        store.apply(alarm("n1", "LINK_DOWN", "CRITICAL", null));
        assertEquals(1, store.snapshot().size());
        store.apply(alarm("n1", "LINK_DOWN", "CRITICAL", "CLEARED"));
        assertTrue(store.snapshot().isEmpty());
    }

    @Test
    void clearOnlyRemovesMatchingTypeOnThatNode() {
        ActiveAlarmStore store = store(3600);
        store.apply(alarm("n1", "LINK_DOWN", "CRITICAL", null));
        store.apply(alarm("n1", "HIGH_TEMP", "MINOR", null));
        store.apply(alarm("n1", "LINK_DOWN", "CRITICAL", "CLEARED"));
        assertEquals(1, store.snapshot().size());
        assertEquals("HIGH_TEMP", store.snapshot().get(0).type());
    }

    @Test
    void escalationReplacesEarlierSeverity() {
        ActiveAlarmStore store = store(3600);
        store.apply(alarm("n1", "LINK_DOWN", "MAJOR", null));
        store.apply(alarm("n1", "LINK_DOWN", "CRITICAL", null));
        assertEquals(1, store.snapshot().size());
        assertEquals("CRITICAL", store.snapshot().get(0).severity());
    }

    @Test
    void neverClearedAlarmsExpireAfterTtl() {
        ActiveAlarmStore store = store(-1);   // negative TTL: everything is already stale
        store.apply(alarm("n1", "LINK_DOWN", "CRITICAL", null));
        assertTrue(store.snapshot().isEmpty());
    }

    @Test
    void oversizedFreeTextIsTruncatedNotRejected() {
        ActiveAlarmStore store = store(3600);
        store.apply(new AlarmEvent("a", "n1", "MAJOR", "T", "x".repeat(5000), Instant.now(), null));
        assertEquals(500, store.snapshot().get(0).message().length());
    }
}
