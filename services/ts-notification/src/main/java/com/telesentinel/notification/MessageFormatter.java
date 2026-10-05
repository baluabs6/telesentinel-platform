package com.telesentinel.notification;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/** Turns event JSON into short chat messages. Subscriber numbers are masked to the last 4 digits. */
public final class MessageFormatter {

    private MessageFormatter() { }

    public static String fraud(JsonNode alert) {
        return "*[%s] Fraud alert: %s*\nSubscriber: %s | Score: %s/100\n%s".formatted(
                text(alert, "severity"), text(alert, "ruleId"), mask(text(alert, "subscriber")),
                text(alert, "score"), text(alert, "reason"));
    }

    public static String incident(JsonNode incident) {
        List<String> impacted = new ArrayList<>();
        incident.path("impactedNodes").forEach(n -> impacted.add(n.asText()));
        return "*[%s] Incident on %s*\n%s\nDownstream alarmed: %s | Alarms: %s\nIncident id: %s".formatted(
                text(incident, "severity"), text(incident, "rootNode"), text(incident, "summary"),
                impacted.isEmpty() ? "none" : String.join(", ", impacted),
                text(incident, "alarmCount"), text(incident, "id"));
    }

    static String mask(String msisdn) {
        return msisdn.length() <= 4 ? "****" : "***" + msisdn.substring(msisdn.length() - 4);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? "n/a" : v.asText();
    }
}
