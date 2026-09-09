package ai.govbiz.core.account.repository.mapper

import java.time.LocalDateTime

/** MyBatis가 소셜 연결 한 행을 읽고 쓰기 위한 DB 행 값입니다. */
data class AccountSocialIdentityDbRow(
    var id: Long = 0,
    var accountId: Long = 0,
    var provider: String = "",
    var providerUserId: String = "",
    var email: String? = null,
    var linkedAt: LocalDateTime? = null,
)
