package com.telesentinel.correlation.engine;

import com.telesentinel.correlation.config.CorrelationProperties;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class TopologyService {

    private final Map<String, CorrelationProperties.Node> nodes;

    public TopologyService(CorrelationProperties props) {
        this.nodes = props.topology() == null || props.topology().nodes() == null
                ? Map.of() : props.topology().nodes();
    }

    public boolean isKnown(String node) {
        return nodes.containsKey(node);
    }

    /** Ancestors ordered nearest first, farthest last. Cycle-safe. */
    public List<String> ancestors(String node) {
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        seen.add(node);
        CorrelationProperties.Node current = nodes.get(node);
        while (current != null && current.parent() != null && seen.add(current.parent())) {
            result.add(current.parent());
            current = nodes.get(current.parent());
        }
        return result;
    }

    /** Subscribers served by this node and everything below it. */
    public long subtreeSubscribers(String root) {
        long total = 0;
        for (Map.Entry<String, CorrelationProperties.Node> e : nodes.entrySet()) {
            if (e.getKey().equals(root) || ancestors(e.getKey()).contains(root)) {
                total += e.getValue().subscribers();
            }
        }
        return total;
    }
}
