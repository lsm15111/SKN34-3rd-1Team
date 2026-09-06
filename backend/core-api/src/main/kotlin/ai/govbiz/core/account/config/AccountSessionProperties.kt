package ai.govbiz.core.account.config

import ai.govbiz.core._common.helper.validatePositiveDuration
import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.account")
class AccountSessionProperties(
    sessionTtl: Duration?,
) {

    /** 발급한 세션 토큰이 유효한 기간입니다. */
    val sessionTtl: Duration = sessionTtl
        ?: throw NullPointerException("app.account.session-ttl must be configured")

    init {
        validatePositiveDuration(this.sessionTtl, "app.account.session-ttl")
    }
}
