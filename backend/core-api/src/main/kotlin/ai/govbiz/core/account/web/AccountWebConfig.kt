package ai.govbiz.core.account.web

import ai.govbiz.core.account.service.AccountSessionService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * 로그인이 필요한 Controller가 [ai.govbiz.core.account.domain.Account] 파라미터를 받을 수 있게 합니다.
 *
 * 세션 Service는 실제 요청을 처리할 때 조회합니다. 웹 계층만 띄우는 Controller 테스트에서도 이 설정이
 * 함께 로드되므로 생성 시점에 Service 빈을 요구하지 않습니다.
 */
@Configuration(proxyBeanMethods = false)
class AccountWebConfig(
    private val sessionServiceProvider: ObjectProvider<AccountSessionService>,
) : WebMvcConfigurer {

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(AuthenticatedAccountArgumentResolver { sessionServiceProvider.getObject() })
    }
}
