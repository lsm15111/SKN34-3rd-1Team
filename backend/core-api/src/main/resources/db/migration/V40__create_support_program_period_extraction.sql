-- 목록 API가 접수 기간을 주지 않는 제공처(MSIT)의 공식 첨부 공고문에서 추출한 신청 기간입니다.
-- 키 컬럼은 support_program과 같은 기본 collation을 사용해 조인합니다.
CREATE TABLE support_program_period_extraction (
 source_code VARCHAR(64) NOT NULL,
 source_program_id VARCHAR(255) NOT NULL,
 status VARCHAR(32) NOT NULL,
 application_start_date DATE NULL,
 application_end_date DATE NULL,
 evidence_text VARCHAR(300) NULL,
 reason_code VARCHAR(64) NULL,
 extractor_version INT NOT NULL,
 attempt_count INT NOT NULL DEFAULT 0,
 checked_at DATETIME(6) NOT NULL,
 next_check_at DATETIME(6) NOT NULL,
 PRIMARY KEY (source_code, source_program_id),
 CONSTRAINT chk_period_extraction_status CHECK (status IN ('EXTRACTED','NOT_FOUND','DOCUMENT_UNAVAILABLE','RETRY_WAITING')),
 CONSTRAINT chk_period_extraction_dates CHECK ((status = 'EXTRACTED' AND application_end_date IS NOT NULL AND evidence_text IS NOT NULL AND (application_start_date IS NULL OR application_start_date <= application_end_date)) OR (status <> 'EXTRACTED' AND application_start_date IS NULL AND application_end_date IS NULL)),
 CONSTRAINT chk_period_extraction_attempt CHECK (attempt_count >= 0),
 INDEX idx_period_extraction_due (source_code, next_check_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
