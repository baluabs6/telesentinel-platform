package com.telesentinel.correlation.persistence;

import com.telesentinel.correlation.model.AlarmEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "active_alarms")
public class ActiveAlarmEntity {

    @Id @Column(name = "alarm_key") private String key;
    @Column(name = "node_id") private String nodeId;
    @Column(name = "alarm_type") private String alarmType;
    private String severity;
    @Column(name = "alarm_id") private String alarmId;
    private String message;
    @Column(name = "raised_at") private Instant raisedAt;
    @Column(name = "last_seen") private Instant lastSeen;

    protected ActiveAlarmEntity() { }

    public static String keyOf(String nodeId, String type) {
        return nodeId + "|" + type;
    }

    public static ActiveAlarmEntity from(AlarmEvent a) {
        ActiveAlarmEntity e = new ActiveAlarmEntity();
        e.key = keyOf(a.nodeId(), a.type());
        e.nodeId = a.nodeId();
        e.alarmType = a.type();
        e.severity = cut(a.severity(), 10);
        e.alarmId = cut(a.alarmId(), 100);
        e.message = cut(a.message(), 500);   // free text from the NMS: never let its length fail the write
        e.raisedAt = a.raisedAt();
        e.lastSeen = Instant.now();
        return e;
    }

    public AlarmEvent toEvent() {
        return new AlarmEvent(alarmId, nodeId, severity, alarmType, message, raisedAt, "RAISED");
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    public String getKey() { return key; }
    public Instant getLastSeen() { return lastSeen; }
}
