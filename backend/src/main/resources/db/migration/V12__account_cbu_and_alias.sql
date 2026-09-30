-- CBU (Clave Bancaria Uniforme, 22 digits) and alias for every account, so money can be received
-- with the identifiers used in Argentina. See com.mindhub.homebanking.domain.Cbu for the format:
--   block 1: bank 999 (fictional) + branch 0001 + check digit = 99900018
--   block 2: 13-digit account part + check digit
-- New accounts get a random account part; existing ones are backfilled from their id. Aliases are
-- stored lower-case; existing accounts get "cuenta.<id>" and their owners can change it.
-- Portable between H2 (dev/test) and PostgreSQL (prod).

ALTER TABLE account ADD COLUMN cbu VARCHAR(22);
ALTER TABLE account ADD COLUMN alias VARCHAR(20);

-- Block 1 plus the zero-padded id (21 digits)...
UPDATE account SET cbu = '99900018' || LPAD(CAST(id AS VARCHAR(13)), 13, '0');

-- ...then the second check digit: weights 3,9,7,1 repeated over digits 9..21.
UPDATE account SET cbu = cbu || CAST(MOD(10 - MOD(
        CAST(SUBSTRING(cbu, 9, 1) AS INTEGER) * 3 + CAST(SUBSTRING(cbu, 10, 1) AS INTEGER) * 9
      + CAST(SUBSTRING(cbu, 11, 1) AS INTEGER) * 7 + CAST(SUBSTRING(cbu, 12, 1) AS INTEGER) * 1
      + CAST(SUBSTRING(cbu, 13, 1) AS INTEGER) * 3 + CAST(SUBSTRING(cbu, 14, 1) AS INTEGER) * 9
      + CAST(SUBSTRING(cbu, 15, 1) AS INTEGER) * 7 + CAST(SUBSTRING(cbu, 16, 1) AS INTEGER) * 1
      + CAST(SUBSTRING(cbu, 17, 1) AS INTEGER) * 3 + CAST(SUBSTRING(cbu, 18, 1) AS INTEGER) * 9
      + CAST(SUBSTRING(cbu, 19, 1) AS INTEGER) * 7 + CAST(SUBSTRING(cbu, 20, 1) AS INTEGER) * 1
      + CAST(SUBSTRING(cbu, 21, 1) AS INTEGER) * 3, 10), 10) AS VARCHAR(1));

UPDATE account SET alias = 'cuenta.' || CAST(id AS VARCHAR(13));

ALTER TABLE account ALTER COLUMN cbu SET NOT NULL;
ALTER TABLE account ALTER COLUMN alias SET NOT NULL;
ALTER TABLE account ADD CONSTRAINT uk_account_cbu UNIQUE (cbu);
ALTER TABLE account ADD CONSTRAINT uk_account_alias UNIQUE (alias);
ALTER TABLE account ADD CONSTRAINT ck_account_cbu CHECK (LENGTH(cbu) = 22);
