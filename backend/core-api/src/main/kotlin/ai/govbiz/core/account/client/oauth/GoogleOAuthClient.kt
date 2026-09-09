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
 * Google OAuth 2.0(OpenID Connect) 웹 서버 흐름입니다. `openid email` scope로 동의를 받고 PKCE(S256)를 함께 씁니다.
 * ID 토큰 서명 검증 대신 액세스 토큰으로 userinfo endpoint를 불러 `sub`·`email`·`email_verified`를 읽습니다.
 * Google은 계정 이메일을 항상 인증한 상태로 주지만 값은 응답의 `email_verified`를 따릅니다.
 */
@Component
class GoogleOAuthClient(
    @param:Qualifier("oauthRestClient") restClient: RestClient,
    properties: OAuthClientProperties,
) : OAuthProviderClient(OAuthProvider.GOOGLE, restClient, properties.google) {

    override fun authorizationUrl(state: String, redirectUri: URI, codeChallenge: String?): URI =
        authorizationUrlBuilder(state, redirectUri, SCOPE)
            .queryParam("code_challenge", requireNotNull(codeChallenge) { "Google login requires a PKCE code challenge" })
            .queryParam("code_challenge_method", "S256")
            // 계정 선택 화면을 매번 띄워 다른 Google 계정으로 바꿔 로그인할 수 있게 합니다.
            .queryParam("prompt", "select_account")
            .build()
            .encode()
            .toUri()

    override fun exchangeCode(exchange: OAuthCodeExchange): String {
        val form = LinkedMultiValueMap<String, String>()
        form.add("grant_type", "authorization_code")
        form.add("code", exchange.code)
        form.add("client_id", settings.clientId)
        form.add("client_secret", settings.clientSecret)
        form.add("redirect_uri", exchange.redirectUri.toString())
        form.add("code_verifier", requireNotNull(exchange.codeVerifier) { "Google login requires a PKCE code verifier" })
        return postTokenRequest(form)
    }

    override fun fetchUserInfo(accessToken: String): OAuthIdentity {
        val body = getUserInfo(accessToken)
        val subject = body.text("sub")
        if (subject.isBlank()) {
            throw OAuthClientException.invalidResponse(provider, "Google user info has no sub", null)
        }
        val email = body.text("email").takeIf(String::isNotBlank)?.let(::normalizeEmail)
        return OAuthIdentity(
            provider = provider,
            providerUserId = subject,
            email = email,
            emailVerified = email != null && body.path("email_verified").asBoolean(false),
        )
    }

    private companion object {
        const val SCOPE = "openid email"
    }
}
