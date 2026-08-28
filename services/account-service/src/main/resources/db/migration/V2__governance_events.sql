CREATE TABLE account_event_inbox (
 event_id VARCHAR(80) PRIMARY KEY, event_type VARCHAR(120) NOT NULL, processed_at DATETIME(6) NOT NULL
);
CREATE TABLE account_event_outbox (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, event_id VARCHAR(80) NOT NULL UNIQUE, event_type VARCHAR(120) NOT NULL,
 payload TEXT NOT NULL, created_at DATETIME(6) NOT NULL, published_at DATETIME(6)
);
CREATE INDEX idx_account_event_outbox_pending ON account_event_outbox(published_at, id);
