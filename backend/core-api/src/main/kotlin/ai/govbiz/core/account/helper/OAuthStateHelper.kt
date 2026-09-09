package ai.govbiz.core.account.helper

import ai.govbiz.core.account.domain.OAuthProvider
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper

/**
 * 소셜 로그인 시작과 콜백 사이를 잇는 값입니다. 브라우저에는 HttpOnly 쿠키로 서명해 두고, 콜백에서 제공처가 돌려준
 * `state`와 비교합니다. 서버 메모리나 DB에 저장하지 않아 프로세스가 여러 개여도 동작합니다.
 */
data class OAuthLoginState(
    val provider: OAuthProvider,
    /** 제공처에 보내고 콜백에서 그대로 돌려받아야 하는 무작위 값입니다. */
    val state: String,
    /** PKCE code_verifier입니다. PKCE를 쓰지 않는 제공처는 null입니다. */
    val codeVerifier: String?,
    /** 로그인 뒤 프런트가 돌아갈 앱 안 경로입니다. */
    val next: String,
    val expiresAt: Instant,
)

/** state 쿠키 값을 만들고 검증합니다. 서명은 세션 JWT와 같은 비밀키를 씁니다. */
object OAuthStateHelper {

    private const val HMAC_ALGORITHM = "HmacSHA256"
    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()
    private val mapper = JsonMapper.builder().build()

    fun randomState(): String = randomToken(32)

    /** RFC 7636 code_verifier(43~128자)입니다. 32바이트를 base64url로 적으면 43자입니다. */
    fun randomCodeVerifier(): String = randomToken(32)

    fun codeChallenge(codeVerifier: String): String =
        encoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(StandardCharsets.US_ASCII)))

    fun issue(state: OAuthLoginState, secret: String): String {
        val payload = encoder.encodeToString(mapper.writeValueAsBytes(Payload.from(state)))
        return "$payload.${encoder.encodeToString(sign(payload, secret))}"
    }

    /** 서명이 맞고 만료 전이면 상태를 돌려주고, 아니면 null입니다. */
    fun verify(value: String?, secret: String, now: Instant): OAuthLoginState? {
        val parts = value?.split('.') ?: return null
        if (parts.size != 2) return null
        val (payload, signature) = parts
        val expected = sign(payload, secret)
        val actual = decodeUrl(signature) ?: return null
        if (!MessageDigest.isEqual(expected, actual)) return null
        val decoded = try {
            mapper.readValue(decodeUrl(payload) ?: return null, Payload::class.java)
        } catch (_: JacksonException) {
            return null
        }
        val provider = OAuthProvider.fromKey(decoded.provider) ?: return null
        val expiresAt = Instant.ofEpochSecond(decoded.exp)
        if (!expiresAt.isAfter(now)) return null
        if (decoded.state.isBlank()) return null
        return OAuthLoginState(provider, decoded.state, decoded.verifier, decoded.next, expiresAt)
    }

    private fun randomToken(bytes: Int): String {
        val buffer = ByteArray(bytes)
        random.nextBytes(buffer)
        return encoder.encodeToString(buffer)
    }

    private fun sign(signingInput: String, secret: String): ByteArray =
        Mac.getInstance(HMAC_ALGORITHM).run {
            init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), HMAC_ALGORITHM))
            doFinal(signingInput.toByteArray(StandardCharsets.US_ASCII))
        }

    private fun decodeUrl(value: String): ByteArray? =
        try {
            decoder.decode(value)
        } catch (_: IllegalArgumentException) {
            null
        }

    /** 쿠키에 직렬화되는 형태입니다. 짧은 이름으로 쿠키 크기를 줄입니다. */
    private data class Payload(
        val provider: String = "",
        val state: String = "",
        val verifier: String? = null,
        val next: String = "",
        val exp: Long = 0,
    ) {
        companion object {
            fun from(state: OAuthLoginState): Payload =
                Payload(state.provider.key, state.state, state.codeVerifier, state.next, state.expiresAt.epochSecond)
        }
    }
}
