CREATE TABLE company (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    business_number CHAR(10) NOT NULL,
    company_name VARCHAR(255) NOT NULL,
    business_status VARCHAR(64) NOT NULL DEFAULT '',
    verified_source VARCHAR(32) NOT NULL,
    verified_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_company_business_number UNIQUE (business_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE account (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    company_id BIGINT UNSIGNED NOT NULL,
    terms_agreed_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_account_email UNIQUE (email),
    CONSTRAINT fk_account_company
        FOREIGN KEY (company_id) REFERENCES company (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE account_session (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    token_hash CHAR(64) NOT NULL,
    account_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_account_session_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_account_session_account
        FOREIGN KEY (account_id) REFERENCES account (id) ON DELETE CASCADE,
    INDEX idx_account_session_account (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
