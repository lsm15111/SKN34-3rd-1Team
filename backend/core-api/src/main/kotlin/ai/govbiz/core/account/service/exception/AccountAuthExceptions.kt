package ai.govbiz.core.account.service.exception

/** 이메일 또는 비밀번호가 맞지 않을 때 발생합니다. 두 경우를 구분하지 않습니다. */
class InvalidCredentialsException : RuntimeException()

/** 가입하려는 이메일이 이미 등록되어 있을 때 발생합니다. 탈퇴한 계정의 이메일도 포함합니다. */
class EmailAlreadyRegisteredException : RuntimeException()

/** 세션 쿠키가 없거나 만료·삭제됐을 때 발생합니다. */
class AuthenticationRequiredException : RuntimeException()

/** 관리자가 정지한 계정으로 로그인하거나 세션을 쓰려 할 때 발생합니다. */
class AccountSuspendedException : RuntimeException()

/** 같은 계정 또는 같은 접속 주소의 로그인 시도가 한도를 넘었을 때 발생합니다. */
class LoginRateLimitedException(val retryAfterSeconds: Int) : RuntimeException() {
    init {
        require(retryAfterSeconds >= 1) { "retryAfterSeconds must be positive" }
    }
}

/** 세션 쿠키가 붙은 상태 변경 요청의 Origin이 허용 목록에 없을 때 발생합니다. */
class SessionOriginRejectedException : RuntimeException()

/** 기업을 아직 등록하지 않은 회원이 기업 조회·수정을 요청했을 때 발생합니다. */
class CompanyNotRegisteredException : RuntimeException()

/** 계정에 이미 기업이 등록되어 있을 때 발생합니다. */
class CompanyAlreadyRegisteredException : RuntimeException()

/** 등록되지 않은 사업자등록번호입니다. */
class BusinessNotFoundException : RuntimeException()

/** 휴업·폐업 사업자는 등록할 수 없습니다. [businessStatus]는 사업자 상태 원문입니다. */
class BusinessNotActiveException(val businessStatus: String) : RuntimeException()

/** 다른 계정이 이미 같은 사업자등록번호를 등록했을 때 발생합니다. */
class BusinessNumberAlreadyRegisteredException : RuntimeException()

/**
 * 소셜 로그인 흐름이 끝나지 못한 이유입니다. 콜백은 JSON이 아니라 프런트 콜백 화면으로 302 하므로
 * [code]가 그대로 `?error=` 값이 됩니다.
 */
class OAuthLoginFailedException(val code: Code, message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    enum class Code {
        /** 제공처 설정(클라이언트 ID)이 비어 있어 이 제공처를 쓸 수 없습니다. */
        PROVIDER_NOT_CONFIGURED,
        /** 사용자가 제공처 동의 화면에서 취소했습니다. */
        PROVIDER_DENIED,
        /** state 쿠키가 없거나 값이 다르거나 만료됐습니다. 다른 탭·오래된 링크·CSRF 시도입니다. */
        STATE_MISMATCH,
        /** 제공처가 이메일을 주지 않았습니다(카카오 이메일 동의 안 함 등). */
        EMAIL_REQUIRED,
        /** 같은 이메일의 계정이 이미 있는데 제공처가 그 이메일을 인증해 주지 않아 자동 연결하지 않았습니다. */
        EMAIL_NOT_VERIFIED,
        /** 제공처 호출 실패·잘못된 응답입니다. */
        PROVIDER_UNAVAILABLE,
        /** 연결된 계정이 정지됐습니다. */
        ACCOUNT_SUSPENDED,
        /** 같은 접속 주소의 로그인 시도가 한도를 넘었습니다. */
        RATE_LIMITED,
    }
}
