CREATE TABLE recruitment_post (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id BIGINT UNSIGNED NOT NULL,
    author_account_id BIGINT UNSIGNED NOT NULL,
    source_code VARCHAR(64) NOT NULL,
    source_program_id VARCHAR(255) NOT NULL,
    title VARCHAR(80) NOT NULL,
    body TEXT NOT NULL,
    our_role VARCHAR(16) NOT NULL,
    wanted_role VARCHAR(16) NOT NULL,
    wanted_company_count TINYINT UNSIGNED NOT NULL,
    wanted_region VARCHAR(60) NOT NULL DEFAULT '',
    required_capabilities JSON NOT NULL,
    closes_on DATE NOT NULL,
    closed_early_at DATETIME(6) NULL,
    hidden_at DATETIME(6) NULL,
    hidden_reason VARCHAR(200) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_recruitment_post_company
        FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT fk_recruitment_post_author
        FOREIGN KEY (author_account_id) REFERENCES account (id),
    CONSTRAINT fk_recruitment_post_program
        FOREIGN KEY (source_code, source_program_id)
        REFERENCES support_program (source_code, source_program_id),
    CONSTRAINT chk_recruitment_post_our_role
        CHECK (our_role IN ('LEAD', 'PARTICIPANT')),
    CONSTRAINT chk_recruitment_post_wanted_role
        CHECK (wanted_role IN ('LEAD', 'PARTICIPANT', 'DEMAND')),
    INDEX idx_recruitment_post_program (source_code, source_program_id),
    INDEX idx_recruitment_post_company (company_id),
    INDEX idx_recruitment_post_closes_on (closes_on)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
