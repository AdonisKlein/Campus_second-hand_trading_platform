USE campus_secondhand;

INSERT INTO users (username, password_hash, nickname, phone, email, role, status)
VALUES
    ('admin', '$2b$12$DMlMB9kSN8zCYPV1dfpbE.Mhf6ftfAqSmfZ1XMeDmffBF3K0NQg8a', 'admin', '13800000000', 'admin@example.com', 'ADMIN', 'ACTIVE'),
    ('alice', '$2a$10$iZdchjEMJXd.39QWJb9dxeDZLSxJuMZa5JfGG7/shWVDhzhLgVoXC', '小艾', '13800000001', 'alice@example.com', 'USER', 'ACTIVE'),
    ('bob', '$2a$10$iZdchjEMJXd.39QWJb9dxeDZLSxJuMZa5JfGG7/shWVDhzhLgVoXC', '小博', '13800000002', 'bob@example.com', 'USER', 'ACTIVE')
AS new_users
ON DUPLICATE KEY UPDATE
    password_hash = new_users.password_hash,
    nickname = new_users.nickname,
    phone = new_users.phone,
    email = new_users.email,
    role = new_users.role,
    status = new_users.status,
    login_failed_count = 0,
    locked_until = NULL;

INSERT INTO items (title, category, price, description, image_url, seller_id)
VALUES
    ('高等数学教材', '书籍', 18.00, '八成新，适合期末复习使用。', 'assets/images/book.svg', 2),
    ('宿舍小台灯', '生活用品', 25.00, '亮度可调，功能正常。', 'assets/images/lamp.svg', 3),
    ('二手蓝牙耳机', '电子产品', 68.00, '续航正常，外观轻微使用痕迹。', 'assets/images/earphone.svg', 2);
