CREATE TABLE chat_conversations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL,
    item_id BIGINT NOT NULL,
    buyer_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    item_title_snapshot VARCHAR(120) NOT NULL,
    item_image_snapshot VARCHAR(255),
    last_message_preview VARCHAR(160),
    last_message_at DATETIME(6),
    next_sequence BIGINT NOT NULL DEFAULT 1,
    buyer_last_read_sequence BIGINT NOT NULL DEFAULT 0,
    seller_last_read_sequence BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_chat_public_id UNIQUE (public_id),
    CONSTRAINT uq_chat_item_participants UNIQUE (item_id, buyer_id, seller_id),
    CONSTRAINT fk_chat_item FOREIGN KEY (item_id) REFERENCES items(id),
    CONSTRAINT fk_chat_buyer FOREIGN KEY (buyer_id) REFERENCES users(id),
    CONSTRAINT fk_chat_seller FOREIGN KEY (seller_id) REFERENCES users(id)
);

CREATE INDEX idx_chat_buyer_recent ON chat_conversations(buyer_id, last_message_at, id);
CREATE INDEX idx_chat_seller_recent ON chat_conversations(seller_id, last_message_at, id);

CREATE TABLE chat_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    sequence_number BIGINT NOT NULL,
    body VARCHAR(2000) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_chat_message_sequence UNIQUE (conversation_id, sequence_number),
    CONSTRAINT fk_chat_message_conversation FOREIGN KEY (conversation_id) REFERENCES chat_conversations(id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_message_sender FOREIGN KEY (sender_id) REFERENCES users(id)
);

CREATE INDEX idx_chat_message_page ON chat_messages(conversation_id, sequence_number, id);
CREATE INDEX idx_chat_message_sender_rate ON chat_messages(sender_id, created_at);

CREATE TABLE chat_blocks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    blocker_id BIGINT NOT NULL,
    blocked_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_chat_block UNIQUE (blocker_id, blocked_id),
    CONSTRAINT chk_chat_block_self CHECK (blocker_id <> blocked_id),
    CONSTRAINT fk_chat_blocker FOREIGN KEY (blocker_id) REFERENCES users(id),
    CONSTRAINT fk_chat_blocked FOREIGN KEY (blocked_id) REFERENCES users(id)
);

CREATE INDEX idx_chat_blocked ON chat_blocks(blocked_id, blocker_id);
