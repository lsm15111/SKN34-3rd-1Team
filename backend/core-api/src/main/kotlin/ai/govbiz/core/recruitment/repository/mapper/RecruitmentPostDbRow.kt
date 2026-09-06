package ai.govbiz.core.recruitment.repository.mapper

import java.time.LocalDate
import java.time.LocalDateTime

/** MyBatis가 모집글 한 행을 작성 기업 컬럼과 함께 읽고 쓰기 위한 DB 행 값입니다. */
data class RecruitmentPostDbRow(
    var id: Long = 0,
    var companyId: Long = 0,
    var authorAccountId: Long = 0,
    var sourceCode: String = "",
    var sourceProgramId: String = "",
    var title: String = "",
    var body: String = "",
    var ourRole: String = "",
    var wantedRole: String = "",
    var wantedCompanyCount: Int = 0,
    var wantedRegion: String = "",
    var requiredCapabilitiesJson: String = "[]",
    var closesOn: LocalDate? = null,
    var closedEarlyAt: LocalDateTime? = null,
    var hiddenAt: LocalDateTime? = null,
    var hiddenReason: String? = null,
    var createdAt: LocalDateTime? = null,
    var updatedAt: LocalDateTime? = null,
    var companyBusinessNumber: String = "",
    var companyName: String = "",
    var companyBusinessStatus: String = "",
)
