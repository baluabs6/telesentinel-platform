-- Alarms that are currently active (raised and not yet cleared). Durable so a restart does not
-- forget live faults and wrongly auto-resolve their incidents.
CREATE TABLE active_alarms (
    alarm_key   VARCHAR(220) PRIMARY KEY,      -- node_id|alarm_type
    node_id     VARCHAR(120) NOT NULL,
    alarm_type  VARCHAR(80)  NOT NULL,
    severity    VARCHAR(10)  NOT NULL,
    alarm_id    VARCHAR(100),
    message     VARCHAR(500),
    raised_at   TIMESTAMPTZ,
    last_seen   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_active_alarms_last_seen ON active_alarms (last_seen);
