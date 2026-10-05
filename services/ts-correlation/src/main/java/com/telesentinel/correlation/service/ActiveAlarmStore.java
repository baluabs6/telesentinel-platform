package com.telesentinel.correlation.service;

import com.telesentinel.correlation.config.CorrelationProperties;
import com.telesentinel.correlation.model.AlarmEvent;
import com.telesentinel.correlation.persistence.ActiveAlarmEntity;
import com.telesentinel.correlation.persistence.ActiveAlarmRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The set of alarms that are currently active, kept in PostgreSQL so it survives restarts.
 * An alarm is active from RAISED until CLEARED. A repeat or escalation of the same (node, type)
 * replaces the earlier row. Rows that are never cleared expire after the TTL (safety net).
 */
@Component
public class ActiveAlarmStore {

    private final ActiveAlarmRepository repository;
    private final Duration ttl;

    public ActiveAlarmStore(ActiveAlarmRepository repository, CorrelationProperties props) {
        this.repository = repository;
        this.ttl = Duration.ofSeconds(props.activeAlarmTtlSeconds());
    }

    public void apply(AlarmEvent alarm) {
        if ("CLEARED".equalsIgnoreCase(alarm.status())) {
            repository.deleteById(ActiveAlarmEntity.keyOf(alarm.nodeId(), alarm.type()));
        } else {
            repository.save(ActiveAlarmEntity.from(alarm));
        }
    }

    public List<AlarmEvent> snapshot() {
        repository.purgeOlderThan(Instant.now().minus(ttl));
        return repository.findAll().stream().map(ActiveAlarmEntity::toEvent).toList();
    }
}
