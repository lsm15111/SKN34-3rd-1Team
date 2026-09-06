CREATE TABLE recruitment_proposal (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    post_id BIGINT UNSIGNED NOT NULL,
    company_id BIGINT UNSIGNED NOT NULL,
    proposer_account_id BIGINT UNSIGNED NOT NULL,
    message VARCHAR(500) NOT NULL,
    decision VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    decided_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_recruitment_proposal_post
        FOREIGN KEY (post_id) REFERENCES recruitment_post (id) ON DELETE CASCADE,
    CONSTRAINT fk_recruitment_proposal_company
        FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT fk_recruitment_proposal_proposer
        FOREIGN KEY (proposer_account_id) REFERENCES account (id),
    CONSTRAINT chk_recruitment_proposal_decision
        CHECK (decision IN ('PENDING', 'ACCEPTED', 'DECLINED', 'WITHDRAWN')),
    UNIQUE KEY uk_recruitment_proposal_post_company (post_id, company_id),
    INDEX idx_recruitment_proposal_company (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
