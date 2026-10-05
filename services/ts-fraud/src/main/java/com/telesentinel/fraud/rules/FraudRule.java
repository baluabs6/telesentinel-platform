package com.telesentinel.fraud.rules;

import com.telesentinel.fraud.model.CdrEvent;
import java.util.Optional;

public interface FraudRule {
    Optional<RuleHit> evaluate(CdrEvent cdr);
}
