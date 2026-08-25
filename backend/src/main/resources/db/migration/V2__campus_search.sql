ALTER TABLE users ADD COLUMN campus_region VARCHAR(40) DEFAULT '学院路校区';
ALTER TABLE users ADD COLUMN credit_score INT NOT NULL DEFAULT 100;
ALTER TABLE users ADD COLUMN last_active_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

ALTER TABLE items ADD COLUMN region VARCHAR(40) NOT NULL DEFAULT '学院路校区';

CREATE TABLE item_tags (
    item_id BIGINT NOT NULL,
    tag VARCHAR(20) NOT NULL,
    PRIMARY KEY (item_id, tag),
    CONSTRAINT fk_item_tags_item FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE
);

CREATE INDEX idx_items_public_region_price ON items(status, moderation_status, region, price, created_at);
CREATE INDEX idx_users_public_search ON users(status, role, campus_region, credit_score, last_active_at);
CREATE INDEX idx_item_tags_tag ON item_tags(tag, item_id);
