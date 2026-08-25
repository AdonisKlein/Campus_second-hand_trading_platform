CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(80),
    phone VARCHAR(30),
    email VARCHAR(254) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'STUDENT',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    login_failed_count INT NOT NULL DEFAULT 0,
    locked_until DATETIME(6),
    auth_version INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email)
);
CREATE TABLE items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(120) NOT NULL,
    category VARCHAR(40) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    description VARCHAR(1000),
    image_url VARCHAR(255),
    seller_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ON_SALE',
    moderation_status VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_items_seller FOREIGN KEY (seller_id) REFERENCES users(id)
);
CREATE INDEX idx_items_status_created ON items(status, created_at);
CREATE INDEX idx_items_category_status ON items(category, status);
CREATE INDEX idx_items_seller ON items(seller_id);
CREATE TABLE messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    content VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_messages_item FOREIGN KEY (item_id) REFERENCES items(id),
    CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES users(id),
    CONSTRAINT fk_messages_receiver FOREIGN KEY (receiver_id) REFERENCES users(id)
);
CREATE INDEX idx_messages_item_created ON messages(item_id, created_at);
CREATE TABLE trade_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_id BIGINT NOT NULL,
    buyer_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    item_title VARCHAR(120) NOT NULL,
    item_price DECIMAL(10,2) NOT NULL,
    buyer_nickname VARCHAR(80) NOT NULL,
    seller_nickname VARCHAR(80) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_SELLER_CONFIRMATION',
    reservation_expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_orders_item FOREIGN KEY (item_id) REFERENCES items(id),
    CONSTRAINT fk_orders_buyer FOREIGN KEY (buyer_id) REFERENCES users(id),
    CONSTRAINT fk_orders_seller FOREIGN KEY (seller_id) REFERENCES users(id)
);
CREATE INDEX idx_orders_buyer_created ON trade_orders(buyer_id, created_at);
CREATE INDEX idx_orders_seller_created ON trade_orders(seller_id, created_at);
CREATE INDEX idx_orders_expiry ON trade_orders(status, reservation_expires_at);
CREATE TABLE email_verification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    email VARCHAR(254) NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_email_verification_scope UNIQUE (email, purpose)
);
CREATE INDEX idx_email_verification_expiry ON email_verification(expires_at);
CREATE TABLE SPRING_SESSION (
    PRIMARY_ID CHAR(36) NOT NULL PRIMARY KEY,
    SESSION_ID CHAR(36) NOT NULL,
    CREATION_TIME BIGINT NOT NULL,
    LAST_ACCESS_TIME BIGINT NOT NULL,
    MAX_INACTIVE_INTERVAL INT NOT NULL,
    EXPIRY_TIME BIGINT NOT NULL,
    PRINCIPAL_NAME VARCHAR(254),
    CONSTRAINT uq_spring_session_id UNIQUE (SESSION_ID)
);
CREATE INDEX idx_spring_session_expiry ON SPRING_SESSION(EXPIRY_TIME);
CREATE INDEX idx_spring_session_principal ON SPRING_SESSION(PRINCIPAL_NAME);
CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36) NOT NULL,
    ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES BLOB NOT NULL,
    PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT fk_session_attributes FOREIGN KEY (SESSION_PRIMARY_ID)
        REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
);
