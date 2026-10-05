package com.telesentinel.notification;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class MessageFormatterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void fraudMessageMasksSubscriber() throws Exception {
        String json = "{\"ruleId\":\"IRSF\",\"subscriber\":\"+919800001234\",\"severity\":\"CRITICAL\","
                    + "\"score\":82,\"reason\":\"35 min to risky range\"}";
        String msg = MessageFormatter.fraud(mapper.readTree(json));
        assertTrue(msg.contains("***1234"));
        assertFalse(msg.contains("919800001234"));
        assertTrue(msg.contains("CRITICAL"));
    }

    @Test
    void incidentMessageListsDownstreamNodes() throws Exception {
        String json = "{\"id\":\"abc\",\"rootNode\":\"agg-12\",\"severity\":\"CRITICAL\",\"summary\":\"LINK_DOWN on agg-12\","
                    + "\"impactedNodes\":[\"gnb-204\",\"gnb-205\"],\"alarmCount\":3}";
        String msg = MessageFormatter.incident(mapper.readTree(json));
        assertTrue(msg.contains("gnb-204, gnb-205"));
        assertTrue(msg.contains("agg-12"));
    }

    @Test
    void missingFieldsDoNotThrow() throws Exception {
        assertDoesNotThrow(() -> MessageFormatter.incident(mapper.readTree("{}")));
    }
}
