ALTER TABLE items ADD COLUMN reserved_order_id BIGINT NULL;
CREATE TABLE marketplace_event_inbox (event_id VARCHAR(80) PRIMARY KEY, event_type VARCHAR(120) NOT NULL, processed_at TIMESTAMP NOT NULL);
CREATE TABLE marketplace_event_outbox (id BIGINT AUTO_INCREMENT PRIMARY KEY, event_id VARCHAR(80) NOT NULL UNIQUE, event_type VARCHAR(120) NOT NULL, payload TEXT NOT NULL, created_at TIMESTAMP NOT NULL, published_at TIMESTAMP NULL);
