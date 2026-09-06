package ai.govbiz.core.account.repository.mapper

import java.time.LocalDateTime

/** MyBatis가 기업 한 행을 읽고 쓰기 위한 DB 행 값입니다. */
data class CompanyDbRow(
    var id: Long = 0,
    var businessNumber: String = "",
    var companyName: String = "",
    var businessStatus: String = "",
    var verifiedSource: String = "",
    var verifiedAt: LocalDateTime? = null,
)
