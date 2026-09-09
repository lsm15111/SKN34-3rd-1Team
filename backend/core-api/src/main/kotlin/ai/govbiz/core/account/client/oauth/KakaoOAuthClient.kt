package ai.govbiz.core.account.client.oauth

import ai.govbiz.core.account.client.oauth.config.OAuthClientProperties
import ai.govbiz.core.account.client.oauth.exception.OAuthClientException
import ai.govbiz.core.account.domain.OAuthIdentity
import ai.govbiz.core.account.domain.OAuthProvider
import ai.govbiz.core.account.helper.normalizeEmail
import java.net.URI
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient

/**
 * 카카오 로그인 REST API 흐름입니다. 인가 코드 → 토큰(`kauth.kakao.com`) → 사용자 정보(`kapi.kakao.com/v2/user/me`).
 * 회원번호는 `id`, 이메일은 `kakao_account.email`이며 동의항목(`account_email`)을 사용자가 거부하면 없을 수 있습니다.
 * 이메일 인증 여부는 `kakao_account.is_email_verified`와 `is_email_valid`가 모두 true일 때만 인정합니다.
 * 클라이언트 시크릿은 카카오 콘솔에서 켰을 때만 보냅니다.
 */
@Component
class KakaoOAuthClient(
    @param:Qualifier("oauthRestClient") restClient: RestClient,
    properties: OAuthClientProperties,
) : OAuthProviderClient(OAuthProvider.KAKAO, restClient, properties.kakao) {

    override fun authorizationUrl(state: String, redirectUri: URI, codeChallenge: String?): URI =
        authorizationUrlBuilder(state, redirectUri, SCOPE)
            .build()
            .encode()
            .toUri()

    override fun exchangeCode(exchange: OAuthCodeExchange): String {
        val form = LinkedMultiValueMap<String, String>()
        form.add("grant_type", "authorization_code")
        form.add("client_id", settings.clientId)
        if (settings.clientSecret.isNotEmpty()) form.add("client_secret", settings.clientSecret)
        form.add("redirect_uri", exchange.redirectUri.toString())
        form.add("code", exchange.code)
        return postTokenRequest(form)
    }

    override fun fetchUserInfo(accessToken: String): OAuthIdentity {
        val body = getUserInfo(accessToken)
        val id = body.path("id")
        if (!id.isNumber && !id.isTextual) {
            throw OAuthClientException.invalidResponse(provider, "Kakao user info has no id", null)
        }
        val account = body.path("kakao_account")
        val email = account.text("email").takeIf(String::isNotBlank)?.let(::normalizeEmail)
        val verified = email != null &&
            account.path("is_email_verified").asBoolean(false) &&
            account.path("is_email_valid").asBoolean(false)
        return OAuthIdentity(
            provider = provider,
            providerUserId = id.asText(),
            email = email,
            emailVerified = verified,
        )
    }

    private companion object {
        const val SCOPE = "account_email"
    }
}
