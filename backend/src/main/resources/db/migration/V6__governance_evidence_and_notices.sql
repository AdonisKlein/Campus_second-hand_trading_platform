ALTER TABLE users ADD COLUMN status_reason VARCHAR(1000);

ALTER TABLE content_reports ADD COLUMN context_conversation_id BIGINT;
ALTER TABLE content_reports ADD COLUMN evidence_snapshot TEXT;
ALTER TABLE content_reports
    ADD CONSTRAINT fk_report_context_conversation
    FOREIGN KEY (context_conversation_id) REFERENCES chat_conversations(id);

CREATE INDEX idx_reports_reported_user ON content_reports(reported_user_id, created_at, id);
