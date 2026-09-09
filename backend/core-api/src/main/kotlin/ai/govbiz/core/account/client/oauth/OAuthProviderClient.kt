package ai.govbiz.core.account.client.oauth

import ai.govbiz.core._common.helper.executeHttpCall
import ai.govbiz.core.account.client.oauth.config.OAuthClientProperties
import ai.govbiz.core.account.client.oauth.exception.OAuthClientException
import ai.govbiz.core.account.domain.OAuthIdentity
import ai.govbiz.core.account.domain.OAuthProvider
import java.net.URI
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode

/** 인가 코드 교환에 필요한 값입니다. [codeVerifier]는 PKCE를 쓰는 제공처(Google)에만 있습니다. */
data class OAuthCodeExchange(
    val code: String,
    val redirectUri: URI,
    val codeVerifier: String?,
)

/**
 * 제공처 하나의 Authorization Code 흐름입니다. 브라우저를 보낼 동의 화면 URL을 만들고,
 * 돌아온 인가 코드를 서버에서 토큰으로 바꾼 뒤 사용자 정보 API로 회원번호·이메일을 확인합니다.
 * 액세스 토큰은 이 호출 안에서만 쓰고 저장하지 않습니다.
 */
abstract class OAuthProviderClient(
    val provider: OAuthProvider,
    protected val restClient: RestClient,
    protected val settings: OAuthClientProperties.Provider,
) {

    val isEnabled: Boolean
        get() = settings.isEnabled

    /** 사용자를 보낼 동의 화면 URL입니다. [codeChallenge]는 PKCE를 쓰는 제공처만 붙입니다. */
    abstract fun authorizationUrl(state: String, redirectUri: URI, codeChallenge: String?): URI

    /** 인가 코드를 토큰으로 바꾸고 사용자 정보를 읽습니다. 설정이 비어 있으면 [OAuthClientException.notConfigured]입니다. */
    fun fetchIdentity(exchange: OAuthCodeExchange): OAuthIdentity {
        if (!isEnabled) throw OAuthClientException.notConfigured(provider)
        return execute {
            val accessToken = exchangeCode(exchange)
            fetchUserInfo(accessToken)
        }
    }

    protected abstract fun exchangeCode(exchange: OAuthCodeExchange): String

    protected abstract fun fetchUserInfo(accessToken: String): OAuthIdentity

    protected fun requireUrl(url: URI?, name: String): URI =
        url ?: throw IllegalStateException("app.oauth.${provider.key}.$name must be configured")

    protected fun authorizationUrlBuilder(state: String, redirectUri: URI, scope: String): UriComponentsBuilder =
        UriComponentsBuilder.fromUri(requireUrl(settings.authorizeUrl, "authorize-url"))
            .queryParam("response_type", "code")
            .queryParam("client_id", settings.clientId)
            .queryParam("redirect_uri", redirectUri.toString())
            .queryParam("state", state)
            .queryParam("scope", scope)

    /** 토큰 endpoint에 form으로 인가 코드를 보내고 `access_token`을 꺼냅니다. 두 제공처의 형식이 같습니다. */
    protected fun postTokenRequest(form: LinkedMultiValueMap<String, String>): String {
        val body = restClient.post()
            .uri(requireUrl(settings.tokenUrl, "token-url"))
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(JsonNode::class.java)
            ?: throw OAuthClientException.invalidResponse(provider, "${provider.key} token response was empty", null)
        val accessToken = body.path("access_token")
        if (!accessToken.isTextual || accessToken.asText().isBlank()) {
            throw OAuthClientException.invalidResponse(provider, "${provider.key} token response has no access_token", null)
        }
        return accessToken.asText()
    }

    protected fun getUserInfo(accessToken: String): JsonNode =
        restClient.get()
            .uri(requireUrl(settings.userInfoUrl, "user-info-url"))
            .header("Authorization", "Bearer $accessToken")
            .retrieve()
            .body(JsonNode::class.java)
            ?: throw OAuthClientException.invalidResponse(provider, "${provider.key} user info response was empty", null)

    /** 문자열 필드가 없거나 null이면 빈 문자열로 읽습니다. */
    protected fun JsonNode.text(fieldName: String): String {
        val node = path(fieldName)
        return if (node.isMissingNode || node.isNull) "" else node.asText()
    }

    private fun <T> execute(block: () -> T): T =
        try {
            executeHttpCall(
                onTimeout = { exception -> OAuthClientException.timeout(provider, exception.cause ?: exception) },
                onUnavailable = { exception -> OAuthClientException.unavailable(provider, exception.cause ?: exception) },
                onUpstreamError = { exception ->
                    // 제공처 오류 본문에는 토큰이 실릴 수 있어 상태 코드만 남깁니다.
                    OAuthClientException.upstreamError(provider, "${provider.key} OAuth endpoint returned HTTP ${exception.statusCode.value()}", null)
                },
                onInvalidResponse = { exception ->
                    OAuthClientException.invalidResponse(provider, "${provider.key} OAuth response could not be decoded", exception.cause)
                },
                block = block,
            )
        } catch (exception: JacksonException) {
            throw OAuthClientException.invalidResponse(provider, "${provider.key} OAuth response could not be decoded", exception)
        }
}
