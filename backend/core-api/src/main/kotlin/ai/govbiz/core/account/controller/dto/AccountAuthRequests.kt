package ai.govbiz.core.account.controller.dto

import ai.govbiz.core.account.domain.AccountRole
import ai.govbiz.core.account.domain.AccountTier
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** 비밀번호가 로그·예외 메시지에 남지 않도록 data class 대신 toString을 제한한 요청입니다. */
class LoginRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 320)
    val email: String,
    @field:NotBlank
    @field:Size(max = 72)
    val password: String,
    /** "로그인 상태 유지". true면 긴 만료의 영구 쿠키, false면 브라우저를 닫으면 사라지는 세션 쿠키입니다. */
    val rememberMe: Boolean = false,
) {
    override fun toString(): String = "LoginRequest(email=$email, rememberMe=$rememberMe)"
}

/** 회원가입 요청입니다. 비밀번호는 길이(8~72자)만 검사하고, 약관 동의 시각은 서버가 요청 시각으로 기록합니다. */
class SignupRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 320)
    val email: String,
    @field:NotBlank
    @field:Size(min = 8, max = 72)
    val password: String,
) {
    override fun toString(): String = "SignupRequest(email=$email)"
}

/**
 * 개발용 로그인에서 어떤 시드 계정으로 들어갈지 고릅니다. 본문이 없으면 관리자입니다.
 * `tier`(ADMIN·MEMBER·COMPANY)가 우선하고, 없으면 예전 계약대로 `role`(ADMIN·USER)로 관리자·회원을 고릅니다.
 */
data class DevLoginRequest(
    val role: AccountRole = AccountRole.ADMIN,
    val tier: AccountTier? = null,
) {
    val resolvedTier: AccountTier
        get() = tier ?: if (role == AccountRole.ADMIN) AccountTier.ADMIN else AccountTier.MEMBER
}
