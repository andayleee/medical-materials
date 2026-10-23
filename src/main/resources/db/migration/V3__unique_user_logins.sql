-- Authentication requires an unambiguous login, including case and surrounding spaces.
-- Existing conflicting accounts must be resolved explicitly; no users are deleted.
DO $$
BEGIN
    IF EXISTS (SELECT lower(trim(login)) FROM users GROUP BY lower(trim(login)) HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Duplicate user logins exist (case/whitespace insensitive). Resolve them before migration V3.';
    END IF;
    IF EXISTS (SELECT 1 FROM users WHERE length(trim(login)) = 0) THEN
        RAISE EXCEPTION 'Empty user logins exist. Resolve them before migration V3.';
    END IF;
END $$;
UPDATE users SET login = lower(trim(login));
ALTER TABLE users ADD CONSTRAINT users_login_normalized CHECK (login = trim(login) AND length(login) > 0);
CREATE UNIQUE INDEX users_login_unique_idx ON users(lower(login));
