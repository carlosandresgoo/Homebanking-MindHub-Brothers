-- Trusted recipients: with 2FA enabled, large transfers to them need no code (trusting one needs a code).
ALTER TABLE contact ADD COLUMN trusted_at TIMESTAMP;
