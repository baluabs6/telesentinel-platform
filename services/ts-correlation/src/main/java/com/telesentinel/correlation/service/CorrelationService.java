package com.telesentinel.correlation.service;

import com.telesentinel.correlation.config.CorrelationProperties;
import com.telesentinel.correlation.engine.CorrelationEngine;
import com.telesentinel.correlation.engine.TopologyService;
import com.telesentinel.correlation.model.AlarmEvent;
import com.telesentinel.correlation.model.RootCause;
import com.telesentinel.correlation.persistence.IncidentEntity;
import com.telesentinel.correlation.persistence.IncidentRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class CorrelationService {

    private static final Logger log = LoggerFactory.getLogger(CorrelationService.class);

    private final ActiveAlarmStore alarms;
    private final CorrelationEngine engine;
    private final TopologyService topology;
    private final IncidentRepository incidents;
    private final KafkaTemplate<String, Object> kafka;
    private final String incidentsTopic;
    private final Duration resolveGrace;
    private final Set<String> warnedUnknown = ConcurrentHashMap.newKeySet();

    public CorrelationService(ActiveAlarmStore alarms, CorrelationEngine engine, TopologyService topology,
                              IncidentRepository incidents, KafkaTemplate<String, Object> kafka,
                              CorrelationProperties props,
                              @Value("${telesentinel.topics.incidents}") String incidentsTopic) {
        this.alarms = alarms;
        this.engine = engine;
        this.topology = topology;
        this.incidents = incidents;
        this.kafka = kafka;
        this.incidentsTopic = incidentsTopic;
        this.resolveGrace = Duration.ofSeconds(props.resolveGraceSeconds());
    }

    @KafkaListener(topics = "${telesentinel.topics.alarms}")
    public void onAlarm(AlarmEvent alarm) {
        if (alarm == null) {
            return;
        }
        if (!topology.isKnown(alarm.nodeId()) && warnedUnknown.add(alarm.nodeId())) {
            log.warn("Alarm from node '{}' which is not in the topology; its impact is unknown (0 subscribers)",
                    alarm.nodeId());
        }
        alarms.apply(alarm);
    }

    @Scheduled(fixedDelayString = "${telesentinel.correlation.evaluation-interval-ms}")
    public void evaluate() {
        List<RootCause> causes = engine.correlate(alarms.snapshot());
        Map<String, IncidentEntity> open = incidents.findByStatus("OPEN").stream()
                .collect(Collectors.toMap(IncidentEntity::getRootNode, Function.identity(), (a, b) -> a));

        for (RootCause rc : causes) {
            mergeChildIncidents(rc, open);
            upsert(rc, open);
        }
        autoResolve(causes.stream().map(RootCause::rootNode).collect(Collectors.toSet()), open);
    }

    /** A parent fault now explains incidents that were opened earlier for its children: fold them in. */
    private void mergeChildIncidents(RootCause rc, Map<String, IncidentEntity> open) {
        for (String symptom : rc.symptomNodes()) {
            IncidentEntity child = open.remove(symptom);
            if (child != null) {
                child.close("MERGED");
                incidents.save(child);
                log.info("Incident on {} merged into incident on {}", symptom, rc.rootNode());
            }
        }
    }

    private void upsert(RootCause rc, Map<String, IncidentEntity> open) {
        IncidentEntity existing = open.get(rc.rootNode());
        if (existing != null) {
            boolean grew = existing.update(rc);
            incidents.save(existing);
            if (grew) {
                publish(existing);
            }
        } else {
            IncidentEntity created = incidents.save(IncidentEntity.open(rc));
            open.put(rc.rootNode(), created);
            publish(created);
        }
    }

    /** Resolve incidents whose root has had no active alarms for the grace period (alarms cleared or expired). */
    private void autoResolve(Set<String> activeRoots, Map<String, IncidentEntity> open) {
        Instant cutoff = Instant.now().minus(resolveGrace);
        for (IncidentEntity incident : open.values()) {
            if (!activeRoots.contains(incident.getRootNode()) && incident.getUpdatedAt().isBefore(cutoff)) {
                incident.close("AUTO_RESOLVED");
                incidents.save(incident);
            }
        }
    }

    private void publish(IncidentEntity incident) {
        kafka.send(incidentsTopic, incident.getRootNode(), incident.toEvent());
        log.warn("INCIDENT {}", incident.getSummary());
    }
}
