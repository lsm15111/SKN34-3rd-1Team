package ai.govbiz.core.account.service

import ai.govbiz.core.account.repository.AccountRepository
import ai.govbiz.core.account.service.dto.AccountSessionResult
import ai.govbiz.core.account.service.exception.InvalidCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

/** 이메일·비밀번호로 로그인하고 새 세션을 발급합니다. 계정 없음과 비밀번호 불일치는 같은 오류로 답합니다. */
@Service
class AccountLoginService(
    private val repository: AccountRepository,
    private val sessionService: AccountSessionService,
    private val passwordEncoder: PasswordEncoder,
) {

    /** 계정이 없을 때도 비밀번호 비교를 한 번 수행해 응답 시간으로 가입 여부가 드러나지 않게 합니다. */
    private val absentAccountHash: String = requireNotNull(passwordEncoder.encode(ABSENT_ACCOUNT_PASSWORD))

    fun logIn(email: String, password: String): AccountSessionResult {
        val credential = repository.findCredentialByEmail(AccountSignupService.normalizeEmail(email))
        if (credential == null) {
            passwordEncoder.matches(password, absentAccountHash)
            throw InvalidCredentialsException()
        }
        if (!passwordEncoder.matches(password, credential.passwordHash)) {
            throw InvalidCredentialsException()
        }

        val issued = sessionService.issue()
        repository.createSession(credential.account.id, issued.session)
        return sessionService.toResult(issued, credential.account)
    }

    private companion object {
        const val ABSENT_ACCOUNT_PASSWORD = "absent-account-timing-guard"
    }
}
