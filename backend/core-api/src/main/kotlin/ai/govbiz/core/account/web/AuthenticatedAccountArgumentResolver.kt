package ai.govbiz.core.account.web

import ai.govbiz.core.account.domain.Account
import ai.govbiz.core.account.service.AccountSessionService
import org.springframework.core.MethodParameter
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

/**
 * Controller 메서드의 [Account] 파라미터를 `Authorization: Bearer` 세션으로 채웁니다.
 *
 * 파라미터가 non-null이면 세션이 없거나 만료됐을 때 [AccountSessionService.requireAccount]의 예외가 그대로 401이
 * 됩니다. nullable(`Account?`)이면 헤더가 없는 요청에는 null을 넣어 비로그인 조회를 허용하되, 헤더가 있는데
 * 유효하지 않으면 여전히 401입니다.
 */
class AuthenticatedAccountArgumentResolver(
    private val sessionServiceSupplier: () -> AccountSessionService,
) : HandlerMethodArgumentResolver {

    constructor(sessionService: AccountSessionService) : this({ sessionService })

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.parameterType == Account::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Account? {
        val authorization = webRequest.getHeader(HttpHeaders.AUTHORIZATION)
        if (authorization == null && parameter.isOptional) return null
        return sessionServiceSupplier().requireAccount(authorization)
    }
}
