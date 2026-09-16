package ai.govbiz.core.supportprogram.repository.mapper

import java.time.LocalDate
import java.time.LocalDateTime

data class SupportProgramPeriodExtractionDbRow(
    var sourceCode: String = "",
    var sourceProgramId: String = "",
    var status: String = "",
    var applicationStartDate: LocalDate? = null,
    var applicationEndDate: LocalDate? = null,
    var evidenceText: String? = null,
    var reasonCode: String? = null,
    var extractorVersion: Int = 0,
    var attemptCount: Int = 0,
    var checkedAt: LocalDateTime? = null,
    var nextCheckAt: LocalDateTime? = null,
)

/** 신청 기간을 아직 확인하지 않았거나 다시 확인할 시점이 된 공개 공고입니다. */
data class SupportProgramPeriodExtractionTargetDbRow(
    var sourceProgramId: String = "",
    var sourceUrl: String = "",
    var sourceSortTimestamp: String? = null,
    var attemptCount: Int = 0,
)
