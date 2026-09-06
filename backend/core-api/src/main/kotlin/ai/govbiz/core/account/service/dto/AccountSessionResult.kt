package ai.govbiz.core.account.service.dto

import ai.govbiz.core.account.domain.Account
import ai.govbiz.core.account.domain.NewAccountSession
import java.time.OffsetDateTime

/** 가입·로그인 성공 뒤 브라우저에 돌려줄 세션 토큰과 계정입니다. */
data class AccountSessionResult(
    val sessionToken: String,
    val expiresAt: OffsetDateTime,
    val account: Account,
)

/** 저장 전 발급한 세션입니다. 원본 토큰은 결과에만, 해시는 DB에만 갑니다. */
data class IssuedSessionResult(
    val sessionToken: String,
    val session: NewAccountSession,
)
