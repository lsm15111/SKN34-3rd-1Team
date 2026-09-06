package ai.govbiz.core.account.client.bizno.exception

class BiznoClientException private constructor(
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
        fun notConfigured(): BiznoClientException =
            BiznoClientException(
                Failure.NOT_CONFIGURED,
                "Bizno API key is not configured",
                null,
            )

        fun upstreamError(message: String, cause: Throwable?): BiznoClientException =
            BiznoClientException(Failure.UPSTREAM_ERROR, message, cause)

        fun invalidResponse(message: String, cause: Throwable?): BiznoClientException =
            BiznoClientException(Failure.INVALID_RESPONSE, message, cause)

        fun unavailable(cause: Throwable?): BiznoClientException =
            BiznoClientException(
                Failure.UNAVAILABLE,
                "Bizno API could not be reached",
                cause,
            )

        fun timeout(cause: Throwable?): BiznoClientException =
            BiznoClientException(
                Failure.TIMEOUT,
                "Bizno API request timed out",
                cause,
            )
    }
}
