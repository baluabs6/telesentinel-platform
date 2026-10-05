package com.telesentinel.correlation;

import static org.junit.jupiter.api.Assertions.*;

import com.telesentinel.correlation.config.CorrelationProperties;
import com.telesentinel.correlation.config.CorrelationProperties.Node;
import com.telesentinel.correlation.engine.CorrelationEngine;
import com.telesentinel.correlation.engine.TopologyService;
import com.telesentinel.correlation.model.AlarmEvent;
import com.telesentinel.correlation.model.RootCause;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CorrelationEngineTest {

    private final CorrelationEngine engine = new CorrelationEngine(new TopologyService(
            new CorrelationProperties(3600, 30, 10_000, new CorrelationProperties.Topology(Map.of(
                    "core-1", new Node(null, 0),
                    "agg-12", new Node("core-1", 0),
                    "agg-13", new Node("core-1", 0),
                    "gnb-204", new Node("agg-12", 4200),
                    "gnb-205", new Node("agg-12", 3800),
                    "gnb-301", new Node("agg-13", 5100))))));

    private AlarmEvent alarm(String node, String sev, String type) {
        return new AlarmEvent(node + "-" + type, node, sev, type, "", Instant.now(), null);
    }

    @Test
    void parentAlarmSwallowsChildSymptoms() {
        List<RootCause> result = engine.correlate(List.of(
                alarm("gnb-204", "MAJOR", "NO_BACKHAUL"),
                alarm("gnb-205", "MAJOR", "NO_BACKHAUL"),
                alarm("agg-12", "CRITICAL", "LINK_DOWN")));

        assertEquals(1, result.size());
        RootCause rc = result.get(0);
        assertEquals("agg-12", rc.rootNode());
        assertEquals("LINK_DOWN", rc.rootAlarmType());
        assertEquals(List.of("gnb-204", "gnb-205"), rc.symptomNodes());
        assertEquals(3, rc.alarmCount());
        assertEquals(8000, rc.estimatedSubscribers());
    }

    @Test
    void isolatedAlarmIsItsOwnRoot() {
        List<RootCause> result = engine.correlate(List.of(alarm("gnb-204", "MINOR", "HIGH_TEMP")));
        assertEquals(1, result.size());
        assertEquals("gnb-204", result.get(0).rootNode());
        assertTrue(result.get(0).symptomNodes().isEmpty());
    }

    @Test
    void unrelatedBranchesProduceSeparateIncidentsSortedByImpact() {
        List<RootCause> result = engine.correlate(List.of(
                alarm("gnb-204", "MAJOR", "CELL_DOWN"),
                alarm("gnb-301", "MAJOR", "CELL_DOWN")));
        assertEquals(2, result.size());
        assertEquals("gnb-301", result.get(0).rootNode());   // 5100 > 4200
    }

    @Test
    void highestAlarmedAncestorWinsEvenWithGap() {
        // agg-12 is not alarmed, but core-1 is: gnb-204 is still a symptom of core-1
        List<RootCause> result = engine.correlate(List.of(
                alarm("core-1", "CRITICAL", "POWER_FAIL"),
                alarm("gnb-204", "MAJOR", "NO_BACKHAUL")));
        assertEquals(1, result.size());
        assertEquals("core-1", result.get(0).rootNode());
        assertEquals(13100, result.get(0).estimatedSubscribers());
    }
}
