package com.telesentinel.fraud.rules;

import com.telesentinel.fraud.config.FraudProperties;
import com.telesentinel.fraud.model.CdrEvent;
import com.telesentinel.fraud.model.FraudAlert;
import com.telesentinel.fraud.store.VelocityStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Wangiri: one-ring calls to many different numbers in a short window. */
@Component
public class WangiriRule implements FraudRule {

    static final String ID = "WANGIRI";
    private final VelocityStore store;
    private final FraudProperties.Wangiri cfg;

    public WangiriRule(VelocityStore store, FraudProperties props) {
        this.store = store;
        this.cfg = props.wangiri();
    }

    @Override
    public Optional<RuleHit> evaluate(CdrEvent cdr) {
        if (cdr.durationSeconds() > cfg.shortCallSeconds()) {
            return Optional.empty();
        }
        Duration window = Duration.ofSeconds(cfg.windowSeconds());
        long targets = store.addDistinct("wangiri:" + cdr.callingNumber(), cdr.calledNumber(), window);
        if (targets < cfg.distinctTargets()) {
            return Optional.empty();
        }
        int score = (int) Math.min(100, 50 + (targets - cfg.distinctTargets()) * 2);
        String reason = "%d short calls (<=%ds) to %d distinct numbers within %ds"
                .formatted(targets, cfg.shortCallSeconds(), targets, cfg.windowSeconds());
        FraudAlert alert = new FraudAlert(ID, cdr.callingNumber(), "HIGH", score, reason, cdr.cdrId(), Instant.now());
        return Optional.of(new RuleHit(alert, "alert:" + ID + ":" + cdr.callingNumber(), window));
    }
}
