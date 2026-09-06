package ai.govbiz.core.account.controller.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/** 비밀번호가 로그·예외 메시지에 남지 않도록 data class 대신 toString을 제한한 요청입니다. */
class SignupRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 320)
    val email: String,
    @field:NotBlank
    @field:Size(min = 8, max = 72)
    @field:Pattern(regexp = PASSWORD_PATTERN)
    val password: String,
    @field:NotBlank
    @field:Pattern(regexp = BUSINESS_NUMBER_PATTERN)
    val businessNumber: String,
) {
    override fun toString(): String = "SignupRequest(email=$email, businessNumber=$businessNumber)"
}

class LoginRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 320)
    val email: String,
    @field:NotBlank
    @field:Size(max = 72)
    val password: String,
) {
    override fun toString(): String = "LoginRequest(email=$email)"
}

/** 8~72자(BCrypt 입력 한계)이며 영문과 숫자를 각각 1자 이상 포함합니다. */
const val PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*[0-9])[^\\p{C}]{8,72}$"

/** 숫자 10자리이며 `000-00-00000` 형식의 하이픈을 허용합니다. */
const val BUSINESS_NUMBER_PATTERN = "[0-9]{3}-?[0-9]{2}-?[0-9]{5}"
