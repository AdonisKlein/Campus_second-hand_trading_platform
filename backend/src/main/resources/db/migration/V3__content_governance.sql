CREATE TABLE content_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reporter_id BIGINT NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT NOT NULL,
    reported_user_id BIGINT NOT NULL,
    target_summary VARCHAR(500) NOT NULL,
    reason_code VARCHAR(40) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    decision_action VARCHAR(30),
    resolution_note VARCHAR(1000),
    handled_by BIGINT,
    resolved_at DATETIME(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES users(id),
    CONSTRAINT fk_report_reported_user FOREIGN KEY (reported_user_id) REFERENCES users(id),
    CONSTRAINT fk_report_handler FOREIGN KEY (handled_by) REFERENCES users(id),
    CONSTRAINT uq_reporter_target UNIQUE (reporter_id, target_type, target_id),
    CONSTRAINT chk_report_target_type CHECK (target_type IN ('ITEM', 'MESSAGE', 'USER')),
    CONSTRAINT chk_report_status CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED'))
);

CREATE INDEX idx_reports_queue ON content_reports(status, created_at, id);
CREATE INDEX idx_reports_reporter ON content_reports(reporter_id, created_at, id);
CREATE INDEX idx_reports_target ON content_reports(target_type, target_id);

CREATE TABLE report_actions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_id BIGINT NOT NULL,
    admin_id BIGINT NOT NULL,
    result_status VARCHAR(20) NOT NULL,
    action_type VARCHAR(30) NOT NULL,
    note VARCHAR(1000) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_report_action_report FOREIGN KEY (report_id) REFERENCES content_reports(id) ON DELETE CASCADE,
    CONSTRAINT fk_report_action_admin FOREIGN KEY (admin_id) REFERENCES users(id)
);

CREATE INDEX idx_report_actions_history ON report_actions(report_id, created_at, id);
