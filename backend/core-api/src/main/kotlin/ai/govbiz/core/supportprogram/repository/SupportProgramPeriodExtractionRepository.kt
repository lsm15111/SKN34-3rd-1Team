package ai.govbiz.core.supportprogram.repository

import ai.govbiz.core.supportprogram.helper.SupportProgramApplicationPeriod
import ai.govbiz.core.supportprogram.helper.SupportProgramApplicationPeriodExtractorHelper
import ai.govbiz.core.supportprogram.repository.mapper.SupportProgramPeriodExtractionDbRow
import ai.govbiz.core.supportprogram.repository.mapper.SupportProgramPeriodExtractionMapper
import ai.govbiz.core.supportprogram.repository.mapper.SupportProgramPeriodExtractionTargetDbRow
import java.time.LocalDateTime
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class SupportProgramPeriodExtractionRepository(private val mapper: SupportProgramPeriodExtractionMapper) {
    enum class Status { EXTRACTED, NOT_FOUND, DOCUMENT_UNAVAILABLE, RETRY_WAITING }

    fun findDueTargets(sourceCode: String, now: LocalDateTime, limit: Int): List<SupportProgramPeriodExtractionTargetDbRow> =
        mapper.findDueTargets(sourceCode, now, SupportProgramApplicationPeriodExtractorHelper.VERSION, limit)

    /** 현재 추출 규칙으로 기간을 찾은 공고만 반환합니다. 카탈로그 동기화가 기간을 null로 덮어쓰지 않게 합니다. */
    fun findExtracted(sourceCode: String): Map<String, SupportProgramApplicationPeriod> =
        mapper.findExtracted(sourceCode, SupportProgramApplicationPeriodExtractorHelper.VERSION).associate { row ->
            row.sourceProgramId to SupportProgramApplicationPeriod(
                row.applicationStartDate, requireNotNull(row.applicationEndDate), requireNotNull(row.evidenceText),
            )
        }

    /** 저장된 추출 기간과 다른 공개 행을 다시 맞추고 맞춘 건수를 반환합니다. */
    @Transactional
    fun reapplyUnapplied(sourceCode: String): Int {
        val rows = mapper.findUnapplied(sourceCode, SupportProgramApplicationPeriodExtractorHelper.VERSION)
        rows.forEach { row ->
            val period = SupportProgramApplicationPeriod(row.applicationStartDate, requireNotNull(row.applicationEndDate), requireNotNull(row.evidenceText))
            applyToProgram(sourceCode, row.sourceProgramId, period)
        }
        return rows.size
    }

    @Transactional
    fun saveExtracted(
        sourceCode: String,
        sourceProgramId: String,
        period: SupportProgramApplicationPeriod,
        attemptCount: Int,
        checkedAt: LocalDateTime,
        nextCheckAt: LocalDateTime,
    ) {
        mapper.upsert(row(sourceCode, sourceProgramId, Status.EXTRACTED, null, attemptCount, checkedAt, nextCheckAt).copy(
            applicationStartDate = period.startDate, applicationEndDate = period.endDate, evidenceText = period.evidence,
        ))
        applyToProgram(sourceCode, sourceProgramId, period)
    }

    private fun applyToProgram(sourceCode: String, sourceProgramId: String, period: SupportProgramApplicationPeriod) {
        mapper.updateProgramPeriod(
            sourceCode, sourceProgramId, period.startDate, period.endDate, period.displayText(),
            SupportProgramApplicationPeriodExtractorHelper.OFFICIAL_ATTACHMENT_SUMMARY,
        )
    }

    fun saveOutcome(
        sourceCode: String,
        sourceProgramId: String,
        status: Status,
        reasonCode: String,
        attemptCount: Int,
        checkedAt: LocalDateTime,
        nextCheckAt: LocalDateTime,
    ) {
        require(status != Status.EXTRACTED) { "extracted periods must be saved with saveExtracted" }
        mapper.upsert(row(sourceCode, sourceProgramId, status, reasonCode, attemptCount, checkedAt, nextCheckAt))
    }

    private fun row(
        sourceCode: String,
        sourceProgramId: String,
        status: Status,
        reasonCode: String?,
        attemptCount: Int,
        checkedAt: LocalDateTime,
        nextCheckAt: LocalDateTime,
    ) = SupportProgramPeriodExtractionDbRow(
        sourceCode = sourceCode, sourceProgramId = sourceProgramId, status = status.name, reasonCode = reasonCode,
        extractorVersion = SupportProgramApplicationPeriodExtractorHelper.VERSION, attemptCount = attemptCount,
        checkedAt = checkedAt, nextCheckAt = nextCheckAt,
    )
}
