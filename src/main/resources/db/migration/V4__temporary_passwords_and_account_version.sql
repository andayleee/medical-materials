-- Existing accounts, including the bootstrap administrator, retain their passwords.
ALTER TABLE users ADD COLUMN password_change_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN security_version BIGINT NOT NULL DEFAULT 0;
