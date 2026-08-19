ALTER TABLE match_player
    MODIFY COLUMN submitted_code LONGTEXT NULL,
    ADD COLUMN submitted_code_purged_at DATETIME(6) NULL;

CREATE TABLE sensitive_data_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    occurred_at DATETIME(6) NOT NULL,
    action VARCHAR(64) NOT NULL,
    data_type VARCHAR(64) NOT NULL,
    actor VARCHAR(255) NOT NULL,
    match_uuid VARCHAR(255),
    outcome VARCHAR(64) NOT NULL,
    affected_rows INTEGER NOT NULL,
    reason VARCHAR(255),
    CONSTRAINT pk_sensitive_data_audit PRIMARY KEY (id)
) ENGINE=InnoDB;

CREATE INDEX idx_sensitive_audit_occurred_at
    ON sensitive_data_audit (occurred_at);

CREATE INDEX idx_sensitive_audit_match_uuid
    ON sensitive_data_audit (match_uuid, occurred_at);
