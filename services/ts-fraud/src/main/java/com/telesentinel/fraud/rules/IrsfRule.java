package com.telesentinel.fraud.rules;

import com.telesentinel.fraud.config.FraudProperties;
import com.telesentinel.fraud.model.CdrEvent;
import com.telesentinel.fraud.model.FraudAlert;
import com.telesentinel.fraud.store.VelocityStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** IRSF: heavy call minutes to premium or high-risk international destinations. */
@Component
public class IrsfRule implements FraudRule {

    static final String ID = "IRSF";
    private final VelocityStore store;
    private final FraudProperties.Irsf cfg;

    public IrsfRule(VelocityStore store, FraudProperties props) {
        this.store = store;
        this.cfg = props.irsf();
    }

    @Override
    public Optional<RuleHit> evaluate(CdrEvent cdr) {
        if (!isRisky(cdr)) {
            return Optional.empty();
        }
        Duration window = Duration.ofSeconds(cfg.windowSeconds());
        long seconds = store.increment("irsf:" + cdr.callingNumber(), cdr.durationSeconds(), window);
        if (seconds < cfg.maxSeconds()) {
            return Optional.empty();
        }
        int score = (int) Math.min(100, 60 + (seconds - cfg.maxSeconds()) / 60);
        String reason = "%d min to high-risk destinations within %d min (limit %d min)"
                .formatted(seconds / 60, cfg.windowSeconds() / 60, cfg.maxSeconds() / 60);
        FraudAlert alert = new FraudAlert(ID, cdr.callingNumber(), "CRITICAL", score, reason, cdr.cdrId(), Instant.now());
        return Optional.of(new RuleHit(alert, "alert:" + ID + ":" + cdr.callingNumber(), window));
    }

    private boolean isRisky(CdrEvent cdr) {
        if ("PREMIUM".equalsIgnoreCase(cdr.callType())) {
            return true;
        }
        return cfg.riskyPrefixes().stream().anyMatch(p -> cdr.calledNumber().startsWith(p));
    }
}
