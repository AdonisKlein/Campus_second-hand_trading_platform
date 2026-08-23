-- Run the preflight query in README.md and back up the database first.
USE campus_secondhand;

UPDATE users SET email = LOWER(TRIM(email));
UPDATE users SET role = 'STUDENT' WHERE role = 'USER';

ALTER TABLE users
    ADD COLUMN auth_version INT NOT NULL DEFAULT 0 AFTER locked_until,
    MODIFY COLUMN email VARCHAR(254) NOT NULL,
    ADD CONSTRAINT uq_users_email UNIQUE (email);

-- Existing plaintext codes must never remain valid after the security upgrade.
DELETE FROM email_verification;
ALTER TABLE email_verification
    CHANGE COLUMN code code_hash CHAR(64) NOT NULL,
    ADD COLUMN purpose VARCHAR(32) NOT NULL AFTER code_hash,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER used,
    DROP INDEX idx_email_used_expires,
    ADD INDEX idx_email_purpose_used_expires (email, purpose, used, expires_at),
    ADD CONSTRAINT uq_email_verification_scope UNIQUE (email, purpose);
