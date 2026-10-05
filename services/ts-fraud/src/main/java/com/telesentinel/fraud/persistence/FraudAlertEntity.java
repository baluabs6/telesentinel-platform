package com.telesentinel.fraud.persistence;

import com.telesentinel.fraud.model.FraudAlert;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fraud_alerts")
public class FraudAlertEntity {

    @Id private UUID id;
    @Column(name = "rule_id") private String ruleId;
    private String subscriber;
    private String severity;
    private int score;
    private String reason;
    @Column(name = "cdr_id") private String cdrId;
    private String status;
    @Column(name = "created_at") private Instant createdAt;

    protected FraudAlertEntity() { }

    public static FraudAlertEntity from(FraudAlert a) {
        FraudAlertEntity e = new FraudAlertEntity();
        e.id = UUID.randomUUID();
        e.ruleId = a.ruleId();
        e.subscriber = a.subscriber();
        e.severity = a.severity();
        e.score = a.score();
        e.reason = a.reason();
        e.cdrId = a.cdrId();
        e.status = "OPEN";
        e.createdAt = a.createdAt();
        return e;
    }

    public void updateStatus(String newStatus) { this.status = newStatus; }

    public UUID getId() { return id; }
    public String getRuleId() { return ruleId; }
    public String getSubscriber() { return subscriber; }
    public String getSeverity() { return severity; }
    public int getScore() { return score; }
    public String getReason() { return reason; }
    public String getCdrId() { return cdrId; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
