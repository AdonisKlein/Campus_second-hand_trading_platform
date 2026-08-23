USE campus_secondhand;

SET @business_rows = (SELECT (SELECT COUNT(*) FROM users) + (SELECT COUNT(*) FROM items));
CREATE TEMPORARY TABLE local_demo_seed_guard (
    business_rows BIGINT NOT NULL,
    CONSTRAINT chk_local_demo_empty CHECK (business_rows = 0)
);
INSERT INTO local_demo_seed_guard VALUES (@business_rows);
DROP TEMPORARY TABLE local_demo_seed_guard;

INSERT INTO users (username, password_hash, nickname, phone, email, role, status)
VALUES
    ('admin', '$2b$12$DMlMB9kSN8zCYPV1dfpbE.Mhf6ftfAqSmfZ1XMeDmffBF3K0NQg8a', 'admin', '13800000000', 'admin@example.com', 'ADMIN', 'ACTIVE'),
    ('alice', '$2b$12$DMlMB9kSN8zCYPV1dfpbE.Mhf6ftfAqSmfZ1XMeDmffBF3K0NQg8a', '小艾', '13800000001', 'alice@example.com', 'STUDENT', 'ACTIVE'),
    ('bob', '$2b$12$DMlMB9kSN8zCYPV1dfpbE.Mhf6ftfAqSmfZ1XMeDmffBF3K0NQg8a', '小博', '13800000002', 'bob@example.com', 'STUDENT', 'ACTIVE');

SET @alice_id = (SELECT id FROM users WHERE email = 'alice@example.com');
SET @bob_id = (SELECT id FROM users WHERE email = 'bob@example.com');

INSERT INTO items (title, category, price, description, image_url, seller_id)
VALUES
    ('高等数学教材', '书籍', 18.00, '八成新，适合期末复习使用。', 'assets/images/book.svg', @alice_id),
    ('宿舍小台灯', '生活用品', 25.00, '亮度可调，功能正常。', 'assets/images/lamp.svg', @bob_id),
    ('二手蓝牙耳机', '电子产品', 68.00, '续航正常，外观轻微使用痕迹。', 'assets/images/earphone.svg', @alice_id);
