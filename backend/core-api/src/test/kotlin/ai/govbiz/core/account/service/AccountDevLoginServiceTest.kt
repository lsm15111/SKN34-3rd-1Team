package ai.govbiz.core.account.service

import ai.govbiz.core.account.domain.AccountRole
import ai.govbiz.core.account.domain.AccountTier
import ai.govbiz.core.account.domain.Company
import ai.govbiz.core.account.domain.CompanySummary
import ai.govbiz.core.account.domain.NewAccount
import ai.govbiz.core.account.domain.NewCompany
import ai.govbiz.core.account.helper.AccountTestHelper
import ai.govbiz.core.account.helper.AccountTestHelper.NOW
import ai.govbiz.core.account.helper.SessionTokenHelper
import ai.govbiz.core.account.repository.AccountRepository
import ai.govbiz.core.account.repository.CompanyRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.eq
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

@ExtendWith(MockitoExtension::class)
class AccountDevLoginServiceTest {

    @Mock
    private lateinit var repository: AccountRepository

    @Mock
    private lateinit var companyRepository: CompanyRepository

    private val passwordEncoder = BCryptPasswordEncoder(4)

    private lateinit var service: AccountDevLoginService

    @BeforeEach
    fun setUp() {
        service = AccountDevLoginService(
            repository,
            companyRepository,
            AccountSessionService(repository, AccountTestHelper.sessionProperties(), AccountTestHelper.FIXED_CLOCK),
            passwordEncoder,
            AccountTestHelper.devLoginProperties(
                email = " Admin@GovBiz.local ",
                password = "govbiz-admin1",
                memberEmail = "Member@GovBiz.local",
                companyEmail = "Company@GovBiz.local",
            ),
            AccountTestHelper.FIXED_CLOCK,
        )
    }

    @Test
    fun createsTheVerifiedAdminAccountWithTheConfiguredPasswordWhenItDoesNotExist() {
        val admin = AccountTestHelper.account(id = 9L, email = "admin@govbiz.local", role = AccountRole.ADMIN, emailVerifiedAt = NOW)
        var created: NewAccount? = null
        doAnswer { invocation ->
            created = invocation.getArgument(0)
            admin
        }.`when`(repository).createAccount(AccountTestHelper.anyValue())

        val result = service.logInAs(AccountTier.ADMIN)

        val newAccount = requireNotNull(created)
        assertEquals("admin@govbiz.local", newAccount.email)
        assertEquals(AccountRole.ADMIN, newAccount.role)
        assertEquals(NOW, newAccount.emailVerifiedAt)
        assertTrue(passwordEncoder.matches("govbiz-admin1", newAccount.passwordHash))
        assertEquals(admin, result.account)
        assertTrue(result.rememberMe)
        verify(repository).createSession(eq(9L), AccountTestHelper.anyValue())
        verify(companyRepository, never()).createCompany(AccountTestHelper.anyValue())
        assertEquals(9L, requireNotNull(SessionTokenHelper.verify(result.sessionToken, AccountTestHelper.JWT_SECRET, AccountTestHelper.FIXED_CLOCK.instant())).accountId)
    }

    @Test
    fun createsAPlainMemberSeedAccountForTheMemberTier() {
        val member = AccountTestHelper.account(id = 4L, email = "member@govbiz.local", emailVerifiedAt = NOW)
        var created: NewAccount? = null
        doAnswer { invocation ->
            created = invocation.getArgument(0)
            member
        }.`when`(repository).createAccount(AccountTestHelper.anyValue())

        val result = service.logInAs(AccountTier.MEMBER)

        val newAccount = requireNotNull(created)
        assertEquals("member@govbiz.local", newAccount.email)
        assertEquals(AccountRole.USER, newAccount.role)
        assertEquals(member, result.account)
        verify(repository).createSession(eq(4L), AccountTestHelper.anyValue())
        verify(companyRepository, never()).createCompany(AccountTestHelper.anyValue())
    }

    @Test
    fun createsTheCompanyMemberSeedAccountWithTheExampleCompanyForTheCompanyTier() {
        val seed = AccountDevLoginService.SEED_COMPANY
        val summary = CompanySummary(id = 21L, companyName = seed.companyName, businessNumber = seed.businessNumber)
        val withoutCompany = AccountTestHelper.account(id = 7L, email = "company@govbiz.local", emailVerifiedAt = NOW)
        val withCompany = withoutCompany.copy(company = summary)
        var created: NewCompany? = null
        doReturn(withoutCompany).`when`(repository).createAccount(AccountTestHelper.anyValue())
        doAnswer { invocation ->
            created = invocation.getArgument(0)
            company(invocation.getArgument(0))
        }.`when`(companyRepository).createCompany(AccountTestHelper.anyValue())
        doReturn(withCompany).`when`(repository).findById(7L)

        val result = service.logInAs(AccountTier.COMPANY)

        val newCompany = requireNotNull(created)
        assertEquals(7L, newCompany.accountId)
        assertEquals(seed.businessNumber, newCompany.businessNumber)
        assertEquals(seed.companyName, newCompany.companyName)
        assertEquals("01", newCompany.businessStatusCode)
        assertEquals(seed.profile, newCompany.profile)
        assertEquals(NOW, newCompany.businessVerifiedAt)
        assertEquals(withCompany, result.account)
        assertEquals(AccountTier.COMPANY, result.account.tier)
        verify(repository).createSession(eq(7L), AccountTestHelper.anyValue())
    }

    @Test
    fun reusesTheExistingCompanyMemberWithoutRegisteringTheCompanyAgain() {
        val summary = CompanySummary(id = 21L, companyName = "다른 기업", businessNumber = "1112233334")
        val existing = AccountTestHelper.account(id = 7L, email = "company@govbiz.local", emailVerifiedAt = NOW, company = summary)
        doReturn(existing).`when`(repository).findByEmail("company@govbiz.local")

        val account = service.ensureSeedAccount(AccountTier.COMPANY)

        assertEquals(existing, account)
        verify(repository, never()).createAccount(AccountTestHelper.anyValue())
        verify(companyRepository, never()).createCompany(AccountTestHelper.anyValue())
    }

    @Test
    fun reusesTheExistingAccountWithoutCreatingAnotherOne() {
        val admin = AccountTestHelper.account(id = 3L, email = "admin@govbiz.local", role = AccountRole.ADMIN)
        doReturn(admin).`when`(repository).findByEmail("admin@govbiz.local")

        val result = service.logInAs(AccountTier.ADMIN)

        assertEquals(admin, result.account)
        verify(repository, never()).createAccount(AccountTestHelper.anyValue())
        verify(repository).createSession(eq(3L), AccountTestHelper.anyValue())
    }

    private fun company(newCompany: NewCompany): Company =
        Company(
            id = 21L,
            accountId = newCompany.accountId,
            businessNumber = newCompany.businessNumber,
            companyName = newCompany.companyName,
            businessStatus = newCompany.businessStatus,
            businessStatusCode = newCompany.businessStatusCode,
            profile = newCompany.profile,
            businessVerifiedAt = newCompany.businessVerifiedAt,
            createdAt = NOW,
            updatedAt = NOW,
        )
}
