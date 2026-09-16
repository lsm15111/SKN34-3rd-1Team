-- 공식 첨부 공고문의 사업목적·지원대상 원문 발췌입니다. 기간을 찾지 못한 공고에도 저장할 수 있습니다.
ALTER TABLE support_program_period_extraction
 ADD COLUMN summary_text VARCHAR(500) NULL AFTER evidence_text,
 ADD COLUMN target_text VARCHAR(400) NULL AFTER summary_text,
 ADD CONSTRAINT chk_period_extraction_sections CHECK (status IN ('EXTRACTED','NOT_FOUND') OR (summary_text IS NULL AND target_text IS NULL));
