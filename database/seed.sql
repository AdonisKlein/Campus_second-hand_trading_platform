USE campus_secondhand;

INSERT INTO users (username, password_hash, nickname, phone, email)
VALUES
    ('alice', '8fe4c114', '小艾', '13800000001', 'alice@example.com'),
    ('bob', '8fe4c114', '小博', '13800000002', 'bob@example.com')
ON DUPLICATE KEY UPDATE nickname = VALUES(nickname);

INSERT INTO items (title, category, price, description, image_url, seller_id)
VALUES
    ('高等数学教材', '书籍', 18.00, '八成新，适合期末复习使用。', 'assets/images/book.svg', 1),
    ('宿舍小台灯', '生活用品', 25.00, '亮度可调，功能正常。', 'assets/images/lamp.svg', 2),
    ('二手蓝牙耳机', '电子产品', 68.00, '续航正常，外观轻微使用痕迹。', 'assets/images/earphone.svg', 1);

