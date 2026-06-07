CREATE DATABASE IF NOT EXISTS campus_secondhand DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE campus_secondhand;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(80),
    phone VARCHAR(30),
    email VARCHAR(100),
    login_failed_count INT NOT NULL DEFAULT 0,
    locked_until DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(120) NOT NULL,
    category VARCHAR(40) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    description VARCHAR(1000),
    image_url VARCHAR(255),
    seller_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ON_SALE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_items_category_status (category, status),
    INDEX idx_items_seller (seller_id),
    CONSTRAINT fk_items_seller FOREIGN KEY (seller_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    item_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    content VARCHAR(500) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_messages_item (item_id),
    CONSTRAINT fk_messages_item FOREIGN KEY (item_id) REFERENCES items(id),
    CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES users(id),
    CONSTRAINT fk_messages_receiver FOREIGN KEY (receiver_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS trade_orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    item_id BIGINT NOT NULL,
    buyer_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_orders_buyer (buyer_id),
    INDEX idx_orders_seller (seller_id),
    CONSTRAINT fk_orders_item FOREIGN KEY (item_id) REFERENCES items(id),
    CONSTRAINT fk_orders_buyer FOREIGN KEY (buyer_id) REFERENCES users(id),
    CONSTRAINT fk_orders_seller FOREIGN KEY (seller_id) REFERENCES users(id)
);

-- Email verification table for registration codes
CREATE TABLE IF NOT EXISTS email_verification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    email VARCHAR(255) NOT NULL COMMENT '接收验证码的邮箱地址',
    code VARCHAR(10) NOT NULL COMMENT '6位数字验证码',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '验证码创建时间',
    expires_at DATETIME NOT NULL COMMENT '验证码过期时间',
    attempts INT NOT NULL DEFAULT 0 COMMENT '验证尝试次数（超过3次自动失效）',
    used BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否已使用（true=已使用，false=未使用）',

    -- 索引：加速按邮箱查询验证码
    INDEX idx_email (email),
    -- 联合索引：加速查询未过期、未使用的验证码
    INDEX idx_email_used_expires (email, used, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='邮箱验证码存储表';
