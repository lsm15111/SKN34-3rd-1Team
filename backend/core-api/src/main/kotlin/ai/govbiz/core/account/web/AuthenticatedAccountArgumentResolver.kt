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
 * 세션이 없거나 만료됐으면 [AccountSessionService.requireAccount]가 던지는 예외가 그대로 401이 됩니다.
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
    ): Account = sessionServiceSupplier().requireAccount(webRequest.getHeader(HttpHeaders.AUTHORIZATION))
}
