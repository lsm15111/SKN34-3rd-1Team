package ai.govbiz.core.supportprogram.repository.mapper

import java.time.LocalDate
import java.time.LocalDateTime
import org.apache.ibatis.annotations.Mapper

@Mapper
interface SupportProgramPeriodExtractionMapper {
    fun findDueTargets(sourceCode: String, now: LocalDateTime, extractorVersion: Int, limit: Int): List<SupportProgramPeriodExtractionTargetDbRow>
    fun findExtracted(sourceCode: String, extractorVersion: Int): List<SupportProgramPeriodExtractionDbRow>
    fun findUnapplied(sourceCode: String, extractorVersion: Int): List<SupportProgramPeriodExtractionDbRow>
    fun upsert(row: SupportProgramPeriodExtractionDbRow): Int
    fun updateProgramPeriod(
        sourceCode: String,
        sourceProgramId: String,
        applicationStartDate: LocalDate?,
        applicationEndDate: LocalDate,
        applicationPeriodRaw: String,
        summary: String,
    ): Int
}
