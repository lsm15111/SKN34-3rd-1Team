package ai.govbiz.core.account.domain

import java.time.LocalDateTime

/** 가입 시점에 Bizno가 국세청 등록 사업자로 확인한 기업 정보입니다. */
data class VerifiedCompany(
    val businessNumber: String,
    val companyName: String,
    val businessStatus: String,
    val verifiedAt: LocalDateTime,
) {
    init {
        requireBusinessNumber(businessNumber)
        require(companyName.isNotBlank()) { "companyName must not be blank" }
    }
}

/** 저장 전 검증을 마친 새 계정입니다. 이메일은 소문자로 정규화되어 있어야 합니다. */
data class NewAccount(
    val email: String,
    val passwordHash: String,
    val termsAgreedAt: LocalDateTime,
    val company: VerifiedCompany,
) {
    init {
        requireEmail(email)
        require(passwordHash.isNotBlank()) { "passwordHash must not be blank" }
    }
}

/** 발급한 세션 토큰의 해시와 만료 시각입니다. 원본 토큰은 저장하지 않습니다. */
data class NewAccountSession(
    val tokenHash: String,
    val expiresAt: LocalDateTime,
) {
    init {
        require(TOKEN_HASH_PATTERN.matches(tokenHash)) { "tokenHash must be a lowercase SHA-256 hash" }
    }

    private companion object {
        val TOKEN_HASH_PATTERN = Regex("[0-9a-f]{64}")
    }
}
