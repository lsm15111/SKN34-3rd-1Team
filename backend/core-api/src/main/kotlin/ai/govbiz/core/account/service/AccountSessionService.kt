package ai.govbiz.core.account.service

import ai.govbiz.core.account.config.AccountSessionProperties
import ai.govbiz.core.account.domain.Account
import ai.govbiz.core.account.domain.NewAccountSession
import ai.govbiz.core.account.helper.SessionTokenHelper
import ai.govbiz.core.account.repository.AccountRepository
import ai.govbiz.core.account.service.dto.AccountSessionResult
import ai.govbiz.core.account.service.dto.IssuedSessionResult
import ai.govbiz.core.account.service.exception.AuthenticationRequiredException
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

/** 세션 토큰을 발급하고, Authorization 헤더의 토큰으로 로그인한 계정을 확인하거나 로그아웃합니다. */
@Service
class AccountSessionService(
    private val repository: AccountRepository,
    private val properties: AccountSessionProperties,
    @param:Qualifier("seoulClock") private val clock: Clock,
) {

    /** 새 토큰과 저장용 해시·만료 시각을 만듭니다. 저장은 호출한 Service가 담당합니다. */
    fun issue(): IssuedSessionResult {
        val token = SessionTokenHelper.generate()
        return IssuedSessionResult(
            sessionToken = token,
            session = NewAccountSession(
                tokenHash = SessionTokenHelper.hash(token),
                // DB DATETIME(6)와 응답 문자열이 같은 값을 가리키도록 초 단위로 맞춥니다.
                expiresAt = LocalDateTime.now(clock).plus(properties.sessionTtl).truncatedTo(ChronoUnit.SECONDS),
            ),
        )
    }

    fun toResult(issued: IssuedSessionResult, account: Account): AccountSessionResult =
        AccountSessionResult(
            sessionToken = issued.sessionToken,
            expiresAt = issued.session.expiresAt.atZone(clock.zone).toOffsetDateTime(),
            account = account,
        )

    /** `Authorization: Bearer <token>`이 유효한 세션이면 계정을 돌려주고, 아니면 401 예외를 던집니다. */
    fun requireAccount(authorization: String?): Account {
        val token = extractBearerToken(authorization) ?: throw AuthenticationRequiredException()
        return repository.findAccountBySessionTokenHash(SessionTokenHelper.hash(token))
            ?: throw AuthenticationRequiredException()
    }

    /** 헤더의 세션을 삭제합니다. 이미 없거나 만료된 세션도 성공으로 처리하고, 헤더가 없으면 401입니다. */
    fun logOut(authorization: String?) {
        val token = extractBearerToken(authorization) ?: throw AuthenticationRequiredException()
        repository.deleteSessionByTokenHash(SessionTokenHelper.hash(token))
    }

    private fun extractBearerToken(authorization: String?): String? {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX, ignoreCase = true)) return null
        return authorization.substring(BEARER_PREFIX.length).trim().takeIf(String::isNotEmpty)
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
    }
}
