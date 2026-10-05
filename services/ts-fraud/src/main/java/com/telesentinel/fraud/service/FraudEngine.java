package com.telesentinel.fraud.service;

import com.telesentinel.fraud.config.FraudProperties;
import com.telesentinel.fraud.model.CdrEvent;
import com.telesentinel.fraud.model.FraudAlert;
import com.telesentinel.fraud.persistence.FraudAlertEntity;
import com.telesentinel.fraud.persistence.FraudAlertRepository;
import com.telesentinel.fraud.rules.FraudRule;
import com.telesentinel.fraud.rules.RuleHit;
import com.telesentinel.fraud.store.VelocityStore;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Delivery semantics: at-least-once from Kafka, made safe by two guards.
 * (1) a per-CDR "seen" key so a redelivered CDR is not counted twice;
 * (2) alert suppression keys that are released if the alert could not be stored, so the retry still alerts.
 * Known trade-off: if a later rule fails after an earlier rule counted, the retry re-counts that earlier rule.
 */
@Service
public class FraudEngine {

    private static final Logger log = LoggerFactory.getLogger(FraudEngine.class);
    private static final Duration SEEN_TTL = Duration.ofDays(1);

    private final List<FraudRule> rules;
    private final FraudAlertRepository repository;
    private final KafkaTemplate<String, Object> kafka;
    private final VelocityStore store;
    private final Set<String> allowList;
    private final String alertsTopic;

    public FraudEngine(List<FraudRule> rules, FraudAlertRepository repository,
                       KafkaTemplate<String, Object> kafka, VelocityStore store, FraudProperties props,
                       @Value("${telesentinel.topics.fraud-alerts}") String alertsTopic) {
        this.rules = rules;
        this.repository = repository;
        this.kafka = kafka;
        this.store = store;
        this.allowList = Set.copyOf(props.allowList());
        this.alertsTopic = alertsTopic;
    }

    @KafkaListener(topics = "${telesentinel.topics.cdrs}")
    public void onCdr(CdrEvent cdr) {
        if (cdr == null || allowList.contains(cdr.callingNumber())) {
            return;   // undeserializable record (already logged) or an allow-listed sender
        }
        String seenKey = "cdr:seen:" + cdr.cdrId();
        if (!store.markOnce(seenKey, SEEN_TTL)) {
            return;   // redelivery of a CDR we already counted
        }
        try {
            for (FraudRule rule : rules) {
                rule.evaluate(cdr).ifPresent(this::raise);
            }
        } catch (RuntimeException e) {
            store.forget(seenKey);
            throw e;   // let the Kafka error handler retry
        }
    }

    private void raise(RuleHit hit) {
        if (!store.markOnce(hit.suppressionKey(), hit.suppressionTtl())) {
            return;   // already alerted for this subscriber in this window
        }
        FraudAlert alert = hit.alert();
        try {
            repository.save(FraudAlertEntity.from(alert));   // the database is the source of truth
        } catch (RuntimeException e) {
            store.forget(hit.suppressionKey());
            throw e;
        }
        kafka.send(alertsTopic, alert.subscriber(), alert).whenComplete((r, ex) -> {
            if (ex != null) {
                log.error("Alert stored but not published (subscriber {}): {}", mask(alert.subscriber()), ex.getMessage());
            }
        });
        log.warn("FRAUD ALERT rule={} subscriber={} score={} reason={}",
                alert.ruleId(), mask(alert.subscriber()), alert.score(), alert.reason());
    }

    /** Logs are widely readable and long-lived: keep only the last four digits. */
    static String mask(String msisdn) {
        return msisdn == null || msisdn.length() <= 4 ? "****" : "***" + msisdn.substring(msisdn.length() - 4);
    }
}
