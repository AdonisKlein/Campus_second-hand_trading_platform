-- Fault isolation fixture. Password for all accounts: abc123
-- Safe to re-run: only the e2e demo IDs below are replaced.

USE campus_account;
DELETE FROM users WHERE id IN (1, 2, 3)
  OR email IN ('e2e-admin@example.test', 'e2e-buyer@example.test', 'e2e-seller@example.test');
INSERT INTO users (id, username, password_hash, nickname, phone, email, role, status, campus_region, credit_score)
VALUES
  (1, 'e2e_admin', '$2b$12$DMlMB9kSN8zCYPV1dfpbE.Mhf6ftfAqSmfZ1XMeDmffBF3K0NQg8a', 'E2E 管理员', NULL, 'e2e-admin@example.test', 'ADMIN', 'ACTIVE', '学院路校区', 100),
  (2, 'e2e_buyer', '$2b$12$DMlMB9kSN8zCYPV1dfpbE.Mhf6ftfAqSmfZ1XMeDmffBF3K0NQg8a', 'E2E 买家', NULL, 'e2e-buyer@example.test', 'STUDENT', 'ACTIVE', '学院路校区', 105),
  (3, 'e2e_seller', '$2b$12$DMlMB9kSN8zCYPV1dfpbE.Mhf6ftfAqSmfZ1XMeDmffBF3K0NQg8a', 'E2E 卖家', NULL, 'e2e-seller@example.test', 'STUDENT', 'ACTIVE', '沙河校区', 110);

USE campus_marketplace;
DELETE FROM searchable_user_projection WHERE id IN (1, 2, 3);
INSERT INTO searchable_user_projection
  (id, username, nickname, campus_region, credit_score, last_active_at, status, role, created_at, source_version, row_version, updated_at)
VALUES
  (1, 'e2e_admin', 'E2E 管理员', '学院路校区', 100, CURRENT_TIMESTAMP(6), 'ACTIVE', 'ADMIN', CURRENT_TIMESTAMP(6), 0, 0, CURRENT_TIMESTAMP(6)),
  (2, 'e2e_buyer', 'E2E 买家', '学院路校区', 105, CURRENT_TIMESTAMP(6), 'ACTIVE', 'STUDENT', CURRENT_TIMESTAMP(6), 0, 0, CURRENT_TIMESTAMP(6)),
  (3, 'e2e_seller', 'E2E 卖家', '沙河校区', 110, CURRENT_TIMESTAMP(6), 'ACTIVE', 'STUDENT', CURRENT_TIMESTAMP(6), 0, 0, CURRENT_TIMESTAMP(6));
