package ai.govbiz.core.supportprogram.repository.mapper

import java.time.LocalDate
import java.time.LocalDateTime
import org.apache.ibatis.annotations.Mapper

@Mapper
interface SupportProgramPeriodExtractionMapper {
    fun findDueTargets(sourceCode: String, now: LocalDateTime, extractorVersion: Int, limit: Int): List<SupportProgramPeriodExtractionTargetDbRow>
    fun findApplicable(sourceCode: String): List<SupportProgramPeriodExtractionDbRow>
    fun findUnapplied(sourceCode: String): List<SupportProgramPeriodExtractionDbRow>
    fun upsert(row: SupportProgramPeriodExtractionDbRow): Int
    fun updateProgramPeriod(
        sourceCode: String,
        sourceProgramId: String,
        applicationStartDate: LocalDate?,
        applicationEndDate: LocalDate,
        applicationPeriodRaw: String,
        missingSummary: String,
        periodOnlySummary: String,
    ): Int
}
