package ai.govbiz.core.supportprogram.repository

import ai.govbiz.core.supportprogram.helper.MsitNoticeContentHelper
import ai.govbiz.core.supportprogram.helper.MsitNoticeExtraction
import ai.govbiz.core.supportprogram.helper.SupportProgramApplicationPeriod
import ai.govbiz.core.supportprogram.helper.SupportProgramApplicationPeriodExtractorHelper
import ai.govbiz.core.supportprogram.helper.SupportProgramNoticeSections
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

    /** 카탈로그 동기화가 병합할 기간·발췌입니다. 목록 API의 빈 값으로 이미 찾은 내용을 덮어쓰지 않게 합니다. */
    fun findApplicable(sourceCode: String): Map<String, MsitNoticeExtraction> =
        mapper.findApplicable(sourceCode).associate { row -> row.sourceProgramId to row.toExtraction() }

    /** 저장된 추출 기간과 다른 공개 행을 다시 맞추고 맞춘 건수를 반환합니다. */
    @Transactional
    fun reapplyUnapplied(sourceCode: String): Int {
        val rows = mapper.findUnapplied(sourceCode)
        rows.forEach { row -> applyPeriod(sourceCode, row.sourceProgramId, requireNotNull(row.toExtraction().period)) }
        return rows.size
    }

    /** 첨부를 읽은 결과를 저장합니다. 기간을 찾았으면 공개 행의 기간도 즉시 갱신합니다. */
    @Transactional
    fun saveExtraction(
        sourceCode: String,
        sourceProgramId: String,
        extraction: MsitNoticeExtraction,
        attemptCount: Int,
        checkedAt: LocalDateTime,
        nextCheckAt: LocalDateTime,
    ) {
        val period = extraction.period
        mapper.upsert(row(
            sourceCode, sourceProgramId, if (period != null) Status.EXTRACTED else Status.NOT_FOUND,
            if (period != null) null else "PERIOD_NOT_FOUND", attemptCount, checkedAt, nextCheckAt,
        ).copy(
            applicationStartDate = period?.startDate, applicationEndDate = period?.endDate, evidenceText = period?.evidence,
            summaryText = extraction.sections.purpose, targetText = extraction.sections.target,
        ))
        if (period != null) applyPeriod(sourceCode, sourceProgramId, period)
    }

    fun saveFailure(
        sourceCode: String,
        sourceProgramId: String,
        status: Status,
        reasonCode: String,
        attemptCount: Int,
        checkedAt: LocalDateTime,
        nextCheckAt: LocalDateTime,
    ) {
        require(status == Status.DOCUMENT_UNAVAILABLE || status == Status.RETRY_WAITING) { "failures cannot carry extracted content" }
        mapper.upsert(row(sourceCode, sourceProgramId, status, reasonCode, attemptCount, checkedAt, nextCheckAt))
    }

    private fun applyPeriod(sourceCode: String, sourceProgramId: String, period: SupportProgramApplicationPeriod) {
        mapper.updateProgramPeriod(
            sourceCode, sourceProgramId, period.startDate, period.endDate, period.displayText(),
            MsitNoticeContentHelper.MISSING_CONTENT_SUMMARY, MsitNoticeContentHelper.PERIOD_ONLY_SUMMARY,
        )
    }

    private fun SupportProgramPeriodExtractionDbRow.toExtraction() = MsitNoticeExtraction(
        period = applicationEndDate?.let { end -> SupportProgramApplicationPeriod(applicationStartDate, end, requireNotNull(evidenceText)) },
        sections = SupportProgramNoticeSections(purpose = summaryText, target = targetText),
    )

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
