-- Movements get a category (filters, daily transfer limit) and, for transfers, the other account.
ALTER TABLE account_transaction ADD COLUMN category VARCHAR(30) DEFAULT 'OTHER' NOT NULL;
ALTER TABLE account_transaction ADD COLUMN counterparty VARCHAR(20);

UPDATE account_transaction SET category = 'TRANSFER_OUT' WHERE description LIKE 'Transferencia a %';
UPDATE account_transaction SET category = 'TRANSFER_IN' WHERE description LIKE 'Transferencia de %';
UPDATE account_transaction SET category = 'LOAN_DISBURSEMENT' WHERE description LIKE 'Préstamo % acreditado';
UPDATE account_transaction SET category = 'LOAN_PAYMENT' WHERE description LIKE 'Cuota %';
UPDATE account_transaction SET category = 'DEPOSIT' WHERE description LIKE 'Depósito%';

CREATE INDEX ix_transaction_category_date ON account_transaction (category, occurred_at);

-- Optional TOTP second factor (RFC 6238). The secret is stored AES-GCM encrypted, never in clear;
-- totp_last_step rejects reusing a code that was already accepted.
ALTER TABLE client ADD COLUMN totp_secret VARCHAR(255);
ALTER TABLE client ADD COLUMN totp_enabled BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE client ADD COLUMN totp_last_step BIGINT;
