package com.telesentinel.correlation.persistence;

import com.telesentinel.correlation.model.IncidentEvent;
import com.telesentinel.correlation.model.RootCause;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "incidents")
public class IncidentEntity {

    @Id private UUID id;
    @Column(name = "root_node") private String rootNode;
    @Column(name = "root_alarm_type") private String rootAlarmType;
    private String severity;
    @Column(name = "impacted_nodes") private String impactedNodes;
    @Column(name = "alarm_count") private int alarmCount;
    @Column(name = "estimated_subscribers") private long estimatedSubscribers;
    private String summary;
    private String status;
    @Column(name = "opened_at") private Instant openedAt;
    @Column(name = "updated_at") private Instant updatedAt;

    protected IncidentEntity() { }

    public static IncidentEntity open(RootCause rc) {
        IncidentEntity e = new IncidentEntity();
        e.id = UUID.randomUUID();
        e.rootNode = rc.rootNode();
        e.status = "OPEN";
        e.openedAt = Instant.now();
        e.apply(rc);
        return e;
    }

    /** Refreshes from the latest correlation; returns true if the blast radius grew. */
    public boolean update(RootCause rc) {
        int before = impactedList().size();
        apply(rc);
        return impactedList().size() > before;
    }

    private void apply(RootCause rc) {
        this.rootAlarmType = rc.rootAlarmType();
        this.severity = rc.severity();
        this.impactedNodes = String.join(",", rc.symptomNodes());
        this.alarmCount = rc.alarmCount();
        this.estimatedSubscribers = rc.estimatedSubscribers();
        this.summary = "%s on %s is the probable root cause; %d downstream node(s) alarmed; ~%d subscribers affected"
                .formatted(rc.rootAlarmType(), rc.rootNode(), rc.symptomNodes().size(), rc.estimatedSubscribers());
        this.updatedAt = Instant.now();
    }

    public void close(String newStatus) {
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    public List<String> impactedList() {
        return impactedNodes == null || impactedNodes.isBlank()
                ? List.of() : Arrays.asList(impactedNodes.split(","));
    }

    public IncidentEvent toEvent() {
        return new IncidentEvent(id, rootNode, rootAlarmType, severity, impactedList(), alarmCount,
                estimatedSubscribers, summary, status, openedAt);
    }

    public UUID getId() { return id; }
    public String getRootNode() { return rootNode; }
    public String getRootAlarmType() { return rootAlarmType; }
    public String getSeverity() { return severity; }
    public String getImpactedNodes() { return impactedNodes; }
    public int getAlarmCount() { return alarmCount; }
    public long getEstimatedSubscribers() { return estimatedSubscribers; }
    public String getSummary() { return summary; }
    public String getStatus() { return status; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
