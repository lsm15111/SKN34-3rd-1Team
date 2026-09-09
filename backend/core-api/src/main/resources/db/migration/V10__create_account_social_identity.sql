-- 소셜 로그인으로만 만든 계정은 비밀번호가 없으므로 해시 컬럼을 NULL 허용으로 바꿉니다.
ALTER TABLE account
    MODIFY COLUMN password_hash VARCHAR(100) NULL;

CREATE TABLE account_social_identity (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id BIGINT UNSIGNED NOT NULL,
    provider VARCHAR(16) NOT NULL,
    provider_user_id VARCHAR(191) NOT NULL,
    email VARCHAR(320) NULL,
    linked_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_account_social_identity_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT uq_account_social_identity_account_provider UNIQUE (account_id, provider),
    CONSTRAINT chk_account_social_identity_provider CHECK (provider IN ('GOOGLE', 'KAKAO')),
    CONSTRAINT fk_account_social_identity_account
        FOREIGN KEY (account_id) REFERENCES account (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
