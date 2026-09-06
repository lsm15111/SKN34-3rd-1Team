ALTER TABLE account
    ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'USER' AFTER password_hash,
    ADD CONSTRAINT chk_account_role CHECK (role IN ('USER', 'ADMIN'));
