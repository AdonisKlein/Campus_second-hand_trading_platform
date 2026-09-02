-- Local/test-only demo data for the microservice topology.
-- Password for all accounts: abc123
-- Run after Flyway migrations: mysql -uroot -p < scripts/dev/demo-seed.sql
-- Removes only the demo IDs below before recreating them; unrelated rows are preserved.

USE campus_account;
DELETE FROM users WHERE id IN (1001, 1002, 1003)
   OR email IN ('demo-admin@example.test', 'demo-alice@example.test', 'demo-bob@example.test',
                'alice@example.com', 'bob@examplee.com', 'admin@example.com');
INSERT INTO users (id, username, password_hash, nickname, phone, email, role, status, campus_region, credit_score, last_active_at, created_at) VALUES
 (1001, 'admin', '$2b$12$DMlMB9kSN8zCYPV1dfpbE.Mhf6ftfAqSmfZ1XMeDmffBF3K0NQg8a', 'Admin', NULL, 'admin@example.com', 'ADMIN', 'ACTIVE', '学院路校区', 100, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
 (1002, 'alice', '$2b$12$DMlMB9kSN8zCYPV1dfpbE.Mhf6ftfAqSmfZ1XMeDmffBF3K0NQg8a', '小艾', NULL, 'alice@example.com', 'STUDENT', 'ACTIVE', '学院路校区', 108, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
 (1003, 'bob', '$2b$12$DMlMB9kSN8zCYPV1dfpbE.Mhf6ftfAqSmfZ1XMeDmffBF3K0NQg8a', '小博', NULL, 'bob@examplee.com', 'STUDENT', 'ACTIVE', '沙河校区', 102, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));

USE campus_marketplace;
DELETE FROM item_tags WHERE item_id IN (2001, 2002, 2003);
DELETE FROM items WHERE id IN (2001, 2002, 2003);
DELETE FROM searchable_user_projection WHERE id IN (1001, 1002, 1003)
   OR username IN ('demo_admin', 'demo_alice', 'demo_bob');
INSERT INTO searchable_user_projection (id, username, nickname, campus_region, credit_score, last_active_at, status, role, created_at, source_version, row_version, updated_at) VALUES
 (1001, 'admin', 'Admin', '学院路校区', 100, CURRENT_TIMESTAMP(6), 'ACTIVE', 'ADMIN', CURRENT_TIMESTAMP(6), 0, 0, CURRENT_TIMESTAMP(6)),
 (1002, 'alice', '小艾', '学院路校区', 108, CURRENT_TIMESTAMP(6), 'ACTIVE', 'STUDENT', CURRENT_TIMESTAMP(6), 0, 0, CURRENT_TIMESTAMP(6)),
 (1003, 'bob', '小博', '沙河校区', 102, CURRENT_TIMESTAMP(6), 'ACTIVE', 'STUDENT', CURRENT_TIMESTAMP(6), 0, 0, CURRENT_TIMESTAMP(6));
INSERT INTO items (id, title, category, price, description, image_url, region, seller_id, status, moderation_status, version, created_at) VALUES
 (2001, '高等数学教材（测试）', '书籍', 18.00, '八成新，适合期末复习。', NULL, '学院路校区', 1002, 'ON_SALE', 'VISIBLE', 0, CURRENT_TIMESTAMP(6)),
 (2002, '宿舍小台灯（测试）', '生活用品', 25.00, '亮度可调，功能正常。', NULL, '沙河校区', 1003, 'ON_SALE', 'VISIBLE', 0, CURRENT_TIMESTAMP(6)),
 (2003, '二手蓝牙耳机（测试）', '电子产品', 68.00, '续航正常，轻微使用痕迹。', NULL, '学院路校区', 1002, 'ON_SALE', 'VISIBLE', 0, CURRENT_TIMESTAMP(6));
INSERT INTO item_tags (item_id, tag) VALUES (2001, '可小刀'), (2001, '仅自提'), (2002, '支持验货'), (2002, '九成新'), (2003, '可小刀'), (2003, '支持验货');
