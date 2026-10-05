CREATE TABLE incidents (
    id                     UUID PRIMARY KEY,
    root_node              VARCHAR(80)  NOT NULL,
    root_alarm_type        VARCHAR(60)  NOT NULL,
    severity               VARCHAR(10)  NOT NULL,
    impacted_nodes         TEXT         NOT NULL DEFAULT '',
    alarm_count            INT          NOT NULL,
    estimated_subscribers  BIGINT       NOT NULL,
    summary                VARCHAR(600) NOT NULL,
    status                 VARCHAR(20)  NOT NULL,
    opened_at              TIMESTAMPTZ  NOT NULL,
    updated_at             TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_incidents_status_root ON incidents (status, root_node);
CREATE INDEX idx_incidents_opened      ON incidents (opened_at DESC);
