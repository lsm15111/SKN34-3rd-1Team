package ai.govbiz.core.account.domain

import java.time.LocalDateTime

/** 소셜 로그인 제공처입니다. URL·저장 값은 [key]로, DB 컬럼은 enum 이름으로 씁니다. */
enum class OAuthProvider(val key: String) {
    GOOGLE("google"),
    KAKAO("kakao"),
    ;

    companion object {
        /** 경로 세그먼트(`google`·`kakao`)로 찾습니다. 모르는 값은 null입니다. */
        fun fromKey(key: String): OAuthProvider? = entries.firstOrNull { provider -> provider.key == key }
    }
}

/**
 * 제공처가 확인해 준 사용자입니다. [providerUserId]는 제공처 안에서 바뀌지 않는 회원번호이고,
 * [email]은 제공처가 알려 주지 않으면 null, [emailVerified]는 제공처가 그 이메일을 인증했다고 답했을 때만 true입니다.
 */
data class OAuthIdentity(
    val provider: OAuthProvider,
    val providerUserId: String,
    val email: String?,
    val emailVerified: Boolean,
) {
    init {
        require(providerUserId.isNotBlank()) { "providerUserId must not be blank" }
        email?.let(::requireEmail)
    }
}

/** 계정에 연결된 소셜 계정 한 건입니다. 같은 제공처 회원번호는 계정 하나에만 연결됩니다. */
data class SocialIdentity(
    val id: Long,
    val accountId: Long,
    val provider: OAuthProvider,
    val providerUserId: String,
    val email: String?,
    val linkedAt: LocalDateTime,
)

/** 저장 전 검증을 마친 새 소셜 연결입니다. */
data class NewSocialIdentity(
    val accountId: Long,
    val provider: OAuthProvider,
    val providerUserId: String,
    val email: String?,
    val linkedAt: LocalDateTime,
) {
    init {
        require(providerUserId.isNotBlank()) { "providerUserId must not be blank" }
        email?.let(::requireEmail)
    }
}
