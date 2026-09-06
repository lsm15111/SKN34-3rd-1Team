package ai.govbiz.core.account.repository.mapper

import java.time.LocalDateTime

/** MyBatis가 계정 한 행을 소속 기업 컬럼과 함께 읽고 쓰기 위한 DB 행 값입니다. */
data class AccountDbRow(
    var id: Long = 0,
    var email: String = "",
    var passwordHash: String = "",
    var companyId: Long = 0,
    var termsAgreedAt: LocalDateTime? = null,
    var companyBusinessNumber: String = "",
    var companyName: String = "",
    var companyBusinessStatus: String = "",
)
