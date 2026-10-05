package com.telesentinel.correlation.engine;

import com.telesentinel.correlation.model.AlarmEvent;
import com.telesentinel.correlation.model.RootCause;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Topology-based root cause analysis. An alarmed node whose ancestor is also alarmed is treated as
 * a symptom of the highest alarmed ancestor. Pure logic, so it is easy to unit test.
 */
@Component
public class CorrelationEngine {

    private final TopologyService topology;

    public CorrelationEngine(TopologyService topology) {
        this.topology = topology;
    }

    public List<RootCause> correlate(Collection<AlarmEvent> alarms) {
        Map<String, List<AlarmEvent>> byNode =
                alarms.stream().collect(Collectors.groupingBy(AlarmEvent::nodeId, LinkedHashMap::new, Collectors.toList()));

        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (String node : byNode.keySet()) {
            String top = node;
            for (String ancestor : topology.ancestors(node)) {   // nearest to farthest
                if (byNode.containsKey(ancestor)) {
                    top = ancestor;                               // keep the highest alarmed one
                }
            }
            groups.computeIfAbsent(top, k -> new ArrayList<>()).add(node);
        }

        List<RootCause> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> g : groups.entrySet()) {
            String root = g.getKey();
            AlarmEvent worst = byNode.get(root).stream()
                    .max(Comparator.comparingInt(a -> rank(a.severity()))).orElseThrow();
            List<String> symptoms = g.getValue().stream().filter(n -> !n.equals(root)).sorted().toList();
            int count = g.getValue().stream().mapToInt(n -> byNode.get(n).size()).sum();
            result.add(new RootCause(root, worst.type(), worst.severity(), symptoms, count,
                    topology.subtreeSubscribers(root)));
        }
        result.sort(Comparator.comparingLong(RootCause::estimatedSubscribers).reversed());
        return result;
    }

    static int rank(String severity) {
        return switch (severity == null ? "" : severity.toUpperCase()) {
            case "CRITICAL" -> 4;
            case "MAJOR" -> 3;
            case "MINOR" -> 2;
            case "WARNING" -> 1;
            default -> 0;
        };
    }
}
