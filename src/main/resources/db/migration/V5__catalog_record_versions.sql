-- Preserve catalog records and detect stale edits.
ALTER TABLE units ADD COLUMN record_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE categories ADD COLUMN record_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE materials ADD COLUMN record_version BIGINT NOT NULL DEFAULT 0;
