package ai.govbiz.core.account.client.oauth.config

import ai.govbiz.core._common.helper.validateHttpBaseUrl
import ai.govbiz.core._common.helper.validatePositiveDuration
import java.net.URI
import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

/**
 * 소셜 로그인 설정입니다. 클라이언트 ID가 비어 있는 제공처는 꺼진 것으로 보고 버튼·시작 endpoint 모두 막힙니다.
 * 제공처 URL은 테스트에서 가짜 서버로 바꿀 수 있도록 설정으로 두되 기본값은 실제 주소입니다.
 */
@ConfigurationProperties(prefix = "app.oauth")
class OAuthClientProperties(
    redirectBaseUrl: URI?,
    frontendBaseUrl: URI?,
    connectTimeout: Duration?,
    readTimeout: Duration?,
    google: Provider?,
    kakao: Provider?,
) {

    /** 제공처가 브라우저를 돌려보낼 Core API의 공개 origin입니다. Compose에서는 `/api`를 대신 받는 웹 origin입니다. */
    val redirectBaseUrl: URI = redirectBaseUrl
        ?: throw NullPointerException("app.oauth.redirect-base-url must be configured")

    /** 로그인 결과를 알려 줄 프런트 origin입니다. 콜백 화면 `/oauth/callback`이 여기에 있습니다. */
    val frontendBaseUrl: URI = frontendBaseUrl
        ?: throw NullPointerException("app.oauth.frontend-base-url must be configured")
    val connectTimeout: Duration = connectTimeout
        ?: throw NullPointerException("app.oauth.connect-timeout must be configured")
    val readTimeout: Duration = readTimeout
        ?: throw NullPointerException("app.oauth.read-timeout must be configured")

    @NestedConfigurationProperty
    val google: Provider = google ?: Provider(null, null, null, null, null)

    @NestedConfigurationProperty
    val kakao: Provider = kakao ?: Provider(null, null, null, null, null)

    init {
        validateHttpBaseUrl(this.redirectBaseUrl, "app.oauth.redirect-base-url")
        validateHttpBaseUrl(this.frontendBaseUrl, "app.oauth.frontend-base-url")
        validatePositiveDuration(this.connectTimeout, "app.oauth.connect-timeout")
        validatePositiveDuration(this.readTimeout, "app.oauth.read-timeout")
    }

    /** 제공처 한 곳의 설정입니다. 비밀은 로그·toString에 남기지 않습니다. */
    class Provider(
        clientId: String?,
        clientSecret: String?,
        authorizeUrl: URI?,
        tokenUrl: URI?,
        userInfoUrl: URI?,
    ) {
        val clientId: String = clientId?.trim().orEmpty()
        val clientSecret: String = clientSecret?.trim().orEmpty()
        val authorizeUrl: URI? = authorizeUrl
        val tokenUrl: URI? = tokenUrl
        val userInfoUrl: URI? = userInfoUrl

        val isEnabled: Boolean
            get() = clientId.isNotEmpty()

        override fun toString(): String = "Provider(clientId=${if (clientId.isEmpty()) "<empty>" else "<set>"})"
    }
}
