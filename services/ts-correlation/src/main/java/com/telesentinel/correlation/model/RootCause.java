package com.telesentinel.correlation.model;

import java.util.List;

/** Output of correlation: one probable root node plus the downstream nodes that are only symptoms. */
public record RootCause(String rootNode, String rootAlarmType, String severity,
                        List<String> symptomNodes, int alarmCount, long estimatedSubscribers) {
}
