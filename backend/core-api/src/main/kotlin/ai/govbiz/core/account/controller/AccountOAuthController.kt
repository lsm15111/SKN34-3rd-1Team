package ai.govbiz.core.account.controller

import ai.govbiz.core.account.controller.dto.OAuthProvidersResponse
import ai.govbiz.core.account.domain.OAuthProvider
import ai.govbiz.core.account.helper.OAuthStateCookieHelper
import ai.govbiz.core.account.helper.SessionCookieHelper
import ai.govbiz.core.account.service.AccountOAuthService
import ai.govbiz.core.account.service.exception.AccountSuspendedException
import ai.govbiz.core.account.service.exception.LoginRateLimitedException
import ai.govbiz.core.account.service.exception.OAuthLoginFailedException
import ai.govbiz.core.account.service.exception.OAuthLoginFailedException.Code
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.constraints.Size
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * Google·카카오 로그인입니다. 브라우저가 링크로 여는 흐름이라 시작·콜백은 JSON이 아니라 302로 답하고,
 * 결과는 프런트 콜백 화면(`/oauth/callback`)에 `next` 또는 `error`로 알립니다. 세션은 이메일 로그인과 같은 쿠키입니다.
 */
@RestController
@RequestMapping(AccountOAuthService.CALLBACK_PATH_PREFIX)
class AccountOAuthController(
    private val oauthService: AccountOAuthService,
    private val stateCookieHelper: OAuthStateCookieHelper,
    private val sessionCookieHelper: SessionCookieHelper,
) {

    private val log = LoggerFactory.getLogger(AccountOAuthController::class.java)

    /** 로그인·회원가입 화면이 어떤 버튼을 그릴지 정합니다. 설정된 제공처만 소문자 키로 내려줍니다. */
    @GetMapping("/providers")
    fun providers(): OAuthProvidersResponse =
        OAuthProvidersResponse(oauthService.enabledProviders().map(OAuthProvider::key))

    /** 동의 화면으로 보냅니다. 꺼진 제공처는 프런트 콜백 화면에 오류로 알립니다. */
    @GetMapping("/{provider}/start")
    fun start(
        @PathVariable provider: String,
        @RequestParam(required = false) @Size(max = AccountOAuthService.MAX_NEXT_LENGTH) next: String?,
    ): ResponseEntity<Void> {
        val resolved = resolveProvider(provider)
        val started = try {
            oauthService.start(resolved, next)
        } catch (exception: OAuthLoginFailedException) {
            return redirectToFrontend(exception.code)
        }
        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.SET_COOKIE, stateCookieHelper.issue(started.state).toString())
            .header(HttpHeaders.LOCATION, started.authorizationUrl.toString())
            .build()
    }

    /** 제공처가 돌려보낸 요청입니다. 성공하면 세션 쿠키를 발급하고 state 쿠키는 어느 경우든 지웁니다. */
    @GetMapping("/{provider}/callback")
    fun callback(
        @PathVariable provider: String,
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) error: String?,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<Void> {
        val resolved = resolveProvider(provider)
        val stored = stateCookieHelper.read(httpRequest)
        val result = try {
            oauthService.complete(resolved, stored, state, code, error, httpRequest.remoteAddr)
        } catch (exception: OAuthLoginFailedException) {
            if (exception.code == Code.PROVIDER_UNAVAILABLE) log.warn("{} login failed: {}", resolved.key, exception.message)
            return redirectToFrontend(exception.code)
        } catch (_: AccountSuspendedException) {
            return redirectToFrontend(Code.ACCOUNT_SUSPENDED)
        } catch (_: LoginRateLimitedException) {
            return redirectToFrontend(Code.RATE_LIMITED)
        }
        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.SET_COOKIE, stateCookieHelper.expire().toString())
            .header(HttpHeaders.SET_COOKIE, sessionCookieHelper.issue(result.sessionToken, result.rememberMe).toString())
            .header(HttpHeaders.LOCATION, oauthService.frontendCallbackUri(stored?.next, null).toString())
            .build()
    }

    private fun redirectToFrontend(error: Code): ResponseEntity<Void> =
        ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.SET_COOKIE, stateCookieHelper.expire().toString())
            .header(HttpHeaders.LOCATION, oauthService.frontendCallbackUri(null, error).toString())
            .build()

    /** 모르는 제공처 경로는 다른 없는 경로와 같게 404입니다. */
    private fun resolveProvider(key: String): OAuthProvider =
        OAuthProvider.fromKey(key) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
}
