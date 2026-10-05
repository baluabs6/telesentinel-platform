CREATE TABLE fraud_alerts (
    id          UUID PRIMARY KEY,
    rule_id     VARCHAR(40)  NOT NULL,
    subscriber  VARCHAR(20)  NOT NULL,
    severity    VARCHAR(10)  NOT NULL,
    score       INT          NOT NULL,
    reason      VARCHAR(500) NOT NULL,
    cdr_id      VARCHAR(64),
    status      VARCHAR(15)  NOT NULL DEFAULT 'OPEN',
    created_at  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_fraud_alerts_subscriber ON fraud_alerts (subscriber);
CREATE INDEX idx_fraud_alerts_created    ON fraud_alerts (created_at DESC);
