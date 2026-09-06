package ai.govbiz.core.account.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

/** 회원 비밀번호 해시와 세션 설정을 제공합니다. Spring Security filter chain은 사용하지 않습니다. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AccountSessionProperties::class)
class AccountAuthConfig {

    @Bean
    fun accountPasswordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
