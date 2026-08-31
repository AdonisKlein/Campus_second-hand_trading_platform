CREATE TABLE trade_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_id BIGINT NOT NULL, buyer_id BIGINT NOT NULL, seller_id BIGINT NOT NULL,
    item_title VARCHAR(120) NOT NULL, item_price DECIMAL(10,2) NOT NULL, item_image_url VARCHAR(255),
    buyer_nickname VARCHAR(80) NOT NULL, seller_nickname VARCHAR(80) NOT NULL,
    status VARCHAR(32) NOT NULL, saga_state VARCHAR(32) NOT NULL,
    pending_final_status VARCHAR(32), expires_at DATETIME(6) NOT NULL,
    closure_reason VARCHAR(160), created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
    INDEX idx_orders_buyer_created (buyer_id, created_at),
    INDEX idx_orders_seller_created (seller_id, created_at),
    INDEX idx_orders_item_status (item_id, status),
    INDEX idx_orders_expiry (status, expires_at)
);
CREATE TABLE chat_conversations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, public_id VARCHAR(36) NOT NULL,
    item_id BIGINT NOT NULL, buyer_id BIGINT NOT NULL, seller_id BIGINT NOT NULL,
    buyer_nickname VARCHAR(80) NOT NULL, seller_nickname VARCHAR(80) NOT NULL,
    item_title_snapshot VARCHAR(120) NOT NULL, item_image_snapshot VARCHAR(255),
    last_message_preview VARCHAR(160), last_message_at DATETIME(6),
    next_sequence BIGINT NOT NULL, buyer_last_read_sequence BIGINT NOT NULL,
    seller_last_read_sequence BIGINT NOT NULL, created_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_chat_public_id UNIQUE (public_id),
    CONSTRAINT uq_chat_item_participants UNIQUE (item_id,buyer_id,seller_id),
    INDEX idx_chat_buyer (buyer_id,last_message_at), INDEX idx_chat_seller (seller_id,last_message_at)
);
CREATE TABLE chat_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL, sequence_number BIGINT NOT NULL,
    body VARCHAR(2000) NOT NULL, created_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_chat_message_sequence UNIQUE (conversation_id,sequence_number),
    INDEX idx_chat_message_page (conversation_id,sequence_number),
    INDEX idx_chat_message_rate (sender_id,created_at)
);
CREATE TABLE chat_blocks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, blocker_id BIGINT NOT NULL,
    blocked_id BIGINT NOT NULL, created_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_chat_block UNIQUE (blocker_id,blocked_id)
);
CREATE TABLE outbox_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, event_id VARCHAR(80) NOT NULL,
    event_type VARCHAR(120) NOT NULL, payload TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL, published_at DATETIME(6),
    CONSTRAINT uq_trading_outbox_event UNIQUE (event_id),
    INDEX idx_trading_outbox_pending (published_at,id)
);
CREATE TABLE inbox_events (
    event_id VARCHAR(80) PRIMARY KEY, event_type VARCHAR(120) NOT NULL,
    processed_at DATETIME(6) NOT NULL
);
