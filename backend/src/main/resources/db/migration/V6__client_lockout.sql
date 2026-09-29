-- Account status: blocked by an administrator (enabled = false) or temporarily locked after
-- too many failed logins (locked_until in the future).

ALTER TABLE client ADD COLUMN enabled BOOLEAN DEFAULT TRUE NOT NULL;
ALTER TABLE client ADD COLUMN failed_login_attempts INTEGER DEFAULT 0 NOT NULL;
ALTER TABLE client ADD COLUMN locked_until TIMESTAMP WITH TIME ZONE;
