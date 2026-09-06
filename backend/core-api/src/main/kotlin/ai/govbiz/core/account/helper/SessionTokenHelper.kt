package ai.govbiz.core.account.helper

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** 불투명 세션 토큰을 만들고, DB에 저장할 SHA-256 해시로 바꿉니다. */
object SessionTokenHelper {

    private val random = SecureRandom()

    /** 256비트 무작위 값을 base64url(패딩 없음) 문자열로 만듭니다. 원본은 브라우저에만 전달합니다. */
    fun generate(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** 토큰의 소문자 SHA-256 hex 해시입니다. DB에는 이 값만 저장합니다. */
    fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private const val TOKEN_BYTES = 32
}
