package ai.govbiz.core.account.service

import ai.govbiz.core.account.domain.NewAccount
import ai.govbiz.core.account.domain.VerifiedCompany
import ai.govbiz.core.account.repository.AccountRepository
import ai.govbiz.core.account.service.dto.AccountSessionResult
import ai.govbiz.core.account.service.exception.BusinessNotFoundException
import ai.govbiz.core.account.service.exception.EmailAlreadyRegisteredException
import java.time.Clock
import java.time.LocalDateTime
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.dao.DuplicateKeyException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

/**
 * 이메일·비밀번호·사업자등록번호만으로 계정을 만듭니다.
 *
 * 이메일 중복 확인 → Bizno 기업 확인(외부 호출, transaction 밖) → 비밀번호 해시 → 저장 순서이며,
 * 저장은 Repository의 짧은 transaction 하나로 끝납니다.
 */
@Service
class AccountSignupService(
    private val repository: AccountRepository,
    private val biznoBusinessService: BiznoBusinessService,
    private val sessionService: AccountSessionService,
    private val passwordEncoder: PasswordEncoder,
    @param:Qualifier("seoulClock") private val clock: Clock,
) {

    fun signUp(email: String, password: String, businessNumber: String): AccountSessionResult {
        val normalizedEmail = normalizeEmail(email)
        if (repository.findByEmail(normalizedEmail) != null) {
            throw EmailAlreadyRegisteredException()
        }

        val business = biznoBusinessService.findByBusinessNumber(businessNumber).firstOrNull()
            ?: throw BusinessNotFoundException()

        val now = LocalDateTime.now(clock)
        val issued = sessionService.issue()
        val newAccount = NewAccount(
            email = normalizedEmail,
            passwordHash = requireNotNull(passwordEncoder.encode(password)) { "password hash must not be null" },
            termsAgreedAt = now,
            company = VerifiedCompany(
                businessNumber = business.businessNumber,
                companyName = business.companyName,
                businessStatus = business.businessStatus,
                verifiedAt = now,
            ),
        )
        val account = try {
            repository.createAccount(newAccount, issued.session)
        } catch (_: DuplicateKeyException) {
            throw EmailAlreadyRegisteredException()
        }
        return sessionService.toResult(issued, account)
    }

    companion object {
        /** 로그인·가입·저장이 같은 표기를 쓰도록 이메일을 앞뒤 공백 제거·소문자로 정규화합니다. */
        fun normalizeEmail(email: String): String = email.trim().lowercase()
    }
}
