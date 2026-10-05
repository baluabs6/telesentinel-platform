package com.telesentinel.fraud.rules;

import static org.junit.jupiter.api.Assertions.*;

import com.telesentinel.fraud.config.FraudProperties;
import com.telesentinel.fraud.model.CdrEvent;
import com.telesentinel.fraud.store.InMemoryVelocityStore;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FraudRulesTest {

    private final FraudProperties props = new FraudProperties(
            new FraudProperties.Wangiri(6, 5, 300),
            new FraudProperties.Irsf(List.of("+882"), 600, 3600),
            List.of());

    private CdrEvent cdr(String id, String from, String to, long secs, String type) {
        return new CdrEvent(id, from, to, Instant.now(), secs, type);
    }

    @Test
    void wangiriHitsOnceDistinctTargetsReachThreshold() {
        WangiriRule rule = new WangiriRule(new InMemoryVelocityStore(), props);
        for (int i = 0; i < 4; i++) {
            assertTrue(rule.evaluate(cdr("c" + i, "+911", "+9198000000" + i, 3, "INTERNATIONAL")).isEmpty());
        }
        Optional<RuleHit> hit = rule.evaluate(cdr("c4", "+911", "+919800000004", 3, "INTERNATIONAL"));
        assertTrue(hit.isPresent());
        assertEquals("WANGIRI", hit.get().alert().ruleId());
        assertEquals("alert:WANGIRI:+911", hit.get().suppressionKey());
    }

    @Test
    void wangiriIgnoresLongCalls() {
        WangiriRule rule = new WangiriRule(new InMemoryVelocityStore(), props);
        for (int i = 0; i < 10; i++) {
            assertTrue(rule.evaluate(cdr("c" + i, "+911", "+9198000000" + i, 120, "LOCAL")).isEmpty());
        }
    }

    @Test
    void irsfHitsWhenMinutesExceedLimit() {
        IrsfRule rule = new IrsfRule(new InMemoryVelocityStore(), props);
        assertTrue(rule.evaluate(cdr("a", "+912", "+882130001", 300, "INTERNATIONAL")).isEmpty());
        Optional<RuleHit> hit = rule.evaluate(cdr("b", "+912", "+882130002", 400, "INTERNATIONAL"));
        assertTrue(hit.isPresent());
        assertEquals("CRITICAL", hit.get().alert().severity());
        assertEquals("IRSF", hit.get().alert().ruleId());
    }

    @Test
    void irsfIgnoresNormalDestinations() {
        IrsfRule rule = new IrsfRule(new InMemoryVelocityStore(), props);
        assertTrue(rule.evaluate(cdr("a", "+912", "+14155550100", 5000, "INTERNATIONAL")).isEmpty());
    }
}
