package ai.govbiz.core.account.domain

/** Bizno로 확인해 저장한 기업입니다. 여러 담당자 계정이 같은 기업에 속할 수 있습니다. */
data class Company(
    val id: Long,
    val businessNumber: String,
    val companyName: String,
    val businessStatus: String,
) {
    init {
        requireBusinessNumber(businessNumber)
        require(companyName.isNotBlank()) { "companyName must not be blank" }
    }
}

/** 로그인 가능한 담당자 계정입니다. 비밀번호 해시는 포함하지 않습니다. */
data class Account(
    val id: Long,
    val email: String,
    val company: Company,
) {
    init {
        requireEmail(email)
    }
}

/** 로그인 검증에만 쓰는 계정과 비밀번호 해시 조합입니다. 공개 계약으로 노출하지 않습니다. */
data class AccountCredential(
    val account: Account,
    val passwordHash: String,
) {
    init {
        require(passwordHash.isNotBlank()) { "passwordHash must not be blank" }
    }
}

internal fun requireBusinessNumber(businessNumber: String) {
    require(businessNumber.length == 10 && businessNumber.all(Char::isDigit)) {
        "businessNumber must be 10 digits"
    }
}

internal fun requireEmail(email: String) {
    require(email.isNotBlank() && email == email.trim() && email == email.lowercase()) {
        "email must be a trimmed lowercase address"
    }
}
