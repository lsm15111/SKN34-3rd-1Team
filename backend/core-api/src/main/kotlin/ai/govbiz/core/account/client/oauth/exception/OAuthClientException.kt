package ai.govbiz.core.account.client.oauth.exception

import ai.govbiz.core.account.domain.OAuthProvider

/** 제공처 토큰 교환·사용자 정보 조회 실패입니다. 메시지에 코드·토큰·비밀은 남기지 않습니다. */
class OAuthClientException private constructor(
    val provider: OAuthProvider,
    val failure: Failure,
    message: String,
    cause: Throwable?,
) : RuntimeException(message, cause) {

    enum class Failure {
        NOT_CONFIGURED,
        UPSTREAM_ERROR,
        INVALID_RESPONSE,
        UNAVAILABLE,
        TIMEOUT,
    }

    companion object {
        fun notConfigured(provider: OAuthProvider): OAuthClientException =
            OAuthClientException(provider, Failure.NOT_CONFIGURED, "${provider.key} OAuth client is not configured", null)

        fun upstreamError(provider: OAuthProvider, message: String, cause: Throwable?): OAuthClientException =
            OAuthClientException(provider, Failure.UPSTREAM_ERROR, message, cause)

        fun invalidResponse(provider: OAuthProvider, message: String, cause: Throwable?): OAuthClientException =
            OAuthClientException(provider, Failure.INVALID_RESPONSE, message, cause)

        fun unavailable(provider: OAuthProvider, cause: Throwable?): OAuthClientException =
            OAuthClientException(provider, Failure.UNAVAILABLE, "${provider.key} OAuth endpoint could not be reached", cause)

        fun timeout(provider: OAuthProvider, cause: Throwable?): OAuthClientException =
            OAuthClientException(provider, Failure.TIMEOUT, "${provider.key} OAuth request timed out", cause)
    }
}
