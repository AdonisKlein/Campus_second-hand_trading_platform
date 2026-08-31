CREATE TABLE content_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reporter_id BIGINT NOT NULL, reporter_name VARCHAR(80) NOT NULL,
    target_type VARCHAR(20) NOT NULL, target_id BIGINT NOT NULL,
    reported_user_id BIGINT NOT NULL, target_summary VARCHAR(500) NOT NULL,
    reason_code VARCHAR(40) NOT NULL, description VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL, decision_action VARCHAR(30) NOT NULL,
    action_state VARCHAR(20) NOT NULL, action_event_id VARCHAR(80), action_error VARCHAR(500),
    resolution_note VARCHAR(1000), handled_by BIGINT, resolved_at DATETIME(6),
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_reporter_target UNIQUE (reporter_id,target_type,target_id),
    INDEX idx_reports_queue (status,created_at,id),
    INDEX idx_reports_reporter (reporter_id,created_at,id),
    INDEX idx_reports_target (target_type,target_id)
);
CREATE TABLE report_actions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, report_id BIGINT NOT NULL,
    admin_id BIGINT NOT NULL, result_status VARCHAR(20) NOT NULL,
    action_type VARCHAR(30) NOT NULL, action_state VARCHAR(20) NOT NULL,
    event_id VARCHAR(80), note VARCHAR(1000) NOT NULL, created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_report_action_report FOREIGN KEY (report_id) REFERENCES content_reports(id) ON DELETE CASCADE,
    INDEX idx_report_actions_history (report_id,created_at,id)
);
CREATE TABLE outbox_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, event_id VARCHAR(80) NOT NULL,
    event_type VARCHAR(120) NOT NULL, payload TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL, published_at DATETIME(6),
    CONSTRAINT uq_governance_outbox_event UNIQUE (event_id),
    INDEX idx_governance_outbox_pending (published_at,id)
);
CREATE TABLE inbox_events (
    event_id VARCHAR(80) PRIMARY KEY, event_type VARCHAR(120) NOT NULL,
    processed_at DATETIME(6) NOT NULL
);
