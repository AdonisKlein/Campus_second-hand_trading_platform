CREATE TABLE items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(120) NOT NULL,
    category VARCHAR(40) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    description VARCHAR(1000), image_url VARCHAR(255),
    region VARCHAR(40) NOT NULL, seller_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL, moderation_status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0, created_at DATETIME(6) NOT NULL,
    INDEX idx_items_public (status, moderation_status, created_at),
    INDEX idx_items_seller (seller_id, created_at)
);
CREATE TABLE item_tags (
    item_id BIGINT NOT NULL, tag VARCHAR(20) NOT NULL,
    PRIMARY KEY (item_id, tag), INDEX idx_item_tags_tag (tag, item_id)
);
CREATE TABLE messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, item_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL, receiver_id BIGINT NOT NULL,
    content VARCHAR(500) NOT NULL, created_at DATETIME(6) NOT NULL,
    INDEX idx_messages_item_created (item_id, created_at)
);
CREATE TABLE searchable_user_projection (
    id BIGINT PRIMARY KEY, username VARCHAR(80) NOT NULL, nickname VARCHAR(80),
    campus_region VARCHAR(40), credit_score INT NOT NULL, last_active_at DATETIME(6),
    status VARCHAR(20) NOT NULL, role VARCHAR(20) NOT NULL, created_at DATETIME(6) NOT NULL,
    source_version BIGINT NOT NULL DEFAULT 0, row_version BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_projection_search (status, role, campus_region),
    INDEX idx_projection_active (last_active_at)
);
