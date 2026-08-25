-- 只负责创建空数据库。所有表由后端启动时的 Flyway 基线创建。
CREATE DATABASE IF NOT EXISTS campus_secondhand
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
