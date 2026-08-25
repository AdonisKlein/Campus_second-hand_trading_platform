INSERT INTO users (username, password_hash, nickname, phone, email, role, status, campus_region, credit_score)
VALUES
  ('e2e_admin', '$2b$12$DMlMB9kSN8zCYPV1dfpbE.Mhf6ftfAqSmfZ1XMeDmffBF3K0NQg8a', 'E2E 管理员', NULL, 'e2e-admin@example.test', 'ADMIN', 'ACTIVE', '学院路校区', 100),
  ('e2e_buyer', '$2b$12$DMlMB9kSN8zCYPV1dfpbE.Mhf6ftfAqSmfZ1XMeDmffBF3K0NQg8a', 'E2E 买家', NULL, 'e2e-buyer@example.test', 'STUDENT', 'ACTIVE', '学院路校区', 105),
  ('e2e_seller', '$2b$12$DMlMB9kSN8zCYPV1dfpbE.Mhf6ftfAqSmfZ1XMeDmffBF3K0NQg8a', 'E2E 卖家', NULL, 'e2e-seller@example.test', 'STUDENT', 'ACTIVE', '沙河校区', 110);
