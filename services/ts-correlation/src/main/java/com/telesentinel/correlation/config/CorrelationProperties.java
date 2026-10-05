package com.telesentinel.correlation.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * activeAlarmTtlSeconds: an alarm stays active until CLEARED, or until this long passes with no repeat
 *   (safety net for NMS systems that never send clears).
 * resolveGraceSeconds: how long a root must be absent before its incident is auto-resolved.
 */
@ConfigurationProperties("telesentinel.correlation")
public record CorrelationProperties(long activeAlarmTtlSeconds, long resolveGraceSeconds,
                                    long evaluationIntervalMs, Topology topology) {
    public record Topology(Map<String, Node> nodes) { }
    public record Node(String parent, long subscribers) { }
}
