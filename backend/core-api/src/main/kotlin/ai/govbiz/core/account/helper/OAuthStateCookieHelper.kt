package ai.govbiz.core.account.helper

import ai.govbiz.core.account.config.AccountSessionProperties
import ai.govbiz.core.account.service.AccountOAuthService
import jakarta.servlet.http.HttpServletRequest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component

/**
 * 소셜 로그인 상태를 담는 짧은 HttpOnly 쿠키입니다. 제공처에서 돌아오는 요청은 최상위 이동(GET)이라
 * `SameSite=Lax`여도 붙습니다. 경로를 콜백 아래로 좁혀 다른 API 요청에는 실리지 않게 합니다.
 */
@Component
class OAuthStateCookieHelper(
    private val properties: AccountSessionProperties,
    @param:Qualifier("seoulClock") private val clock: Clock,
) {

    fun issue(state: OAuthLoginState): ResponseCookie =
        build(OAuthStateHelper.issue(state, properties.jwtSecret), Duration.between(Instant.now(clock), state.expiresAt))

    fun expire(): ResponseCookie = build("", Duration.ZERO)

    /** 서명·만료가 맞지 않으면 null입니다. */
    fun read(request: HttpServletRequest): OAuthLoginState? =
        OAuthStateHelper.verify(
            request.cookies?.firstOrNull { cookie -> cookie.name == COOKIE_NAME }?.value,
            properties.jwtSecret,
            Instant.now(clock),
        )

    private fun build(value: String, maxAge: Duration): ResponseCookie =
        ResponseCookie.from(COOKIE_NAME, value)
            .httpOnly(true)
            .secure(properties.cookieSecure)
            .sameSite("Lax")
            .path(AccountOAuthService.CALLBACK_PATH_PREFIX)
            .maxAge(maxAge)
            .build()

    companion object {
        const val COOKIE_NAME = "govbiz_oauth"
    }
}
