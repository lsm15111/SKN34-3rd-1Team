package ai.govbiz.core.account.service

import ai.govbiz.core.account.config.AccountDevLoginProperties
import ai.govbiz.core.account.domain.Account
import ai.govbiz.core.account.domain.AccountRole
import ai.govbiz.core.account.domain.AccountTier
import ai.govbiz.core.account.domain.CompanyProfileInput
import ai.govbiz.core.account.domain.NewAccount
import ai.govbiz.core.account.domain.NewCompany
import ai.govbiz.core.account.helper.normalizeEmail
import ai.govbiz.core.account.repository.AccountRepository
import ai.govbiz.core.account.repository.CompanyRepository
import ai.govbiz.core.account.service.dto.AccountSessionResult
import java.time.Clock
import java.time.LocalDateTime
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.dao.DuplicateKeyException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

/**
 * 개발 환경에서 비밀번호 입력 없이 시드 계정으로 로그인합니다.
 *
 * 단계별로 설정한 이메일의 계정이 없으면 이메일 인증까지 끝난 상태로 만들고, 있으면 그대로 씁니다. 기업 회원(T2)은
 * 사업자등록번호 조회 없이 [SEED_COMPANY]를 등록합니다. 만든 계정은 설정한 비밀번호로 일반 로그인도 됩니다.
 * 공개 endpoint는 `app.account.dev-login.enabled`가 켜졌을 때만 등록되고, 개발용 목데이터 시드도 같은 계정을 씁니다.
 */
@Service
class AccountDevLoginService(
    private val repository: AccountRepository,
    private val companyRepository: CompanyRepository,
    private val sessionService: AccountSessionService,
    private val passwordEncoder: PasswordEncoder,
    private val properties: AccountDevLoginProperties,
    @param:Qualifier("seoulClock") private val clock: Clock,
) {

    /** ADMIN이면 관리자(T3), MEMBER면 기업 정보가 없는 회원(T1), COMPANY면 예시 기업을 등록한 회원(T2) 시드 계정으로 세션을 발급합니다. */
    fun logInAs(tier: AccountTier): AccountSessionResult {
        val account = ensureSeedAccount(tier)

        val issued = sessionService.issue(account.id, rememberMe = true)
        repository.createSession(account.id, issued.session)
        return sessionService.toResult(issued, account)
    }

    /** 단계별 시드 계정을 찾거나 만듭니다. COMPANY 계정에 기업이 없으면 예시 기업을 붙입니다. */
    fun ensureSeedAccount(tier: AccountTier): Account {
        val (configuredEmail, role) = when (tier) {
            AccountTier.ADMIN -> properties.email to AccountRole.ADMIN
            AccountTier.MEMBER -> properties.memberEmail to AccountRole.USER
            AccountTier.COMPANY -> properties.companyEmail to AccountRole.USER
        }
        val email = normalizeEmail(configuredEmail)
        val account = repository.findByEmail(email) ?: createSeedAccount(email, role)
        if (tier != AccountTier.COMPANY || account.hasCompany) return account
        return attachSeedCompany(account)
    }

    private fun createSeedAccount(email: String, role: AccountRole): Account =
        try {
            val now = LocalDateTime.now(clock)
            repository.createAccount(
                NewAccount(
                    email = email,
                    passwordHash = requireNotNull(passwordEncoder.encode(properties.password)) { "password hash must not be null" },
                    termsAgreedAt = now,
                    role = role,
                    emailVerifiedAt = now,
                ),
            )
        } catch (_: DuplicateKeyException) {
            // 같은 순간 다른 요청이 먼저 만들었으면 그 계정을 씁니다.
            requireNotNull(repository.findByEmail(email)) { "dev seed account was not readable" }
        }

    private fun attachSeedCompany(account: Account): Account {
        try {
            companyRepository.createCompany(
                NewCompany(
                    accountId = account.id,
                    businessNumber = SEED_COMPANY.businessNumber,
                    companyName = SEED_COMPANY.companyName,
                    businessStatus = SEED_COMPANY.businessStatus,
                    businessStatusCode = SEED_COMPANY.businessStatusCode,
                    profile = SEED_COMPANY.profile,
                    businessVerifiedAt = LocalDateTime.now(clock),
                ),
            )
        } catch (_: DuplicateKeyException) {
            // 같은 순간 다른 요청이 먼저 등록했으면 그 기업을 씁니다.
        }
        return requireNotNull(repository.findById(account.id)) { "dev seed company account was not readable" }
    }

    companion object {
        /** 기업 회원 시드에 붙는 예시 기업입니다. 실제 기업이 아니며 사업자등록번호도 가상 값입니다. */
        val SEED_COMPANY = DevLoginSeedCompany(
            businessNumber = "2208800042",
            companyName = "그루브데이터 주식회사",
            businessStatus = "계속사업자",
            businessStatusCode = "01",
            profile = CompanyProfileInput(
                region = "서울특별시",
                industry = "정보통신업",
                foundedYear = 2022,
                homepageUrl = "https://groovedata.example",
            ),
        )
    }
}

/** 사업자등록번호 조회를 거치지 않고 저장하는 개발용 예시 기업입니다. */
data class DevLoginSeedCompany(
    val businessNumber: String,
    val companyName: String,
    val businessStatus: String,
    val businessStatusCode: String,
    val profile: CompanyProfileInput,
)
