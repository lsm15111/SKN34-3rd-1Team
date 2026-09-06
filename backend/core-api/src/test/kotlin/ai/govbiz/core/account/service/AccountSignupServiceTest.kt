package ai.govbiz.core.account.service

import ai.govbiz.core.account.client.bizno.dto.BiznoBusiness
import ai.govbiz.core.account.domain.NewAccount
import ai.govbiz.core.account.domain.NewAccountSession
import ai.govbiz.core.account.helper.AccountTestHelper
import ai.govbiz.core.account.helper.SessionTokenHelper
import ai.govbiz.core.account.repository.AccountRepository
import ai.govbiz.core.account.service.exception.BusinessNotFoundException
import ai.govbiz.core.account.service.exception.EmailAlreadyRegisteredException
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.dao.DuplicateKeyException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

@ExtendWith(MockitoExtension::class)
class AccountSignupServiceTest {

    @Mock
    private lateinit var repository: AccountRepository

    @Mock
    private lateinit var biznoBusinessService: BiznoBusinessService

    private val passwordEncoder = BCryptPasswordEncoder(4)

    private lateinit var service: AccountSignupService

    @BeforeEach
    fun setUp() {
        service = AccountSignupService(
            repository,
            biznoBusinessService,
            AccountSessionService(repository, AccountTestHelper.sessionProperties(), AccountTestHelper.FIXED_CLOCK),
            passwordEncoder,
            AccountTestHelper.FIXED_CLOCK,
        )
    }

    @Test
    fun checksTheEmailThenBiznoThenStoresTheHashedAccountWithTheVerifiedCompany() {
        val business = BiznoBusiness("1248100998", "삼성전자(주)", "계속사업자")
        doReturn(listOf(business)).`when`(biznoBusinessService).findByBusinessNumber("124-81-00998")
        val account = AccountTestHelper.account()
        var storedAccount: NewAccount? = null
        var storedSession: NewAccountSession? = null
        doAnswer { invocation ->
            storedAccount = invocation.getArgument(0)
            storedSession = invocation.getArgument(1)
            account
        }.`when`(repository).createAccount(AccountTestHelper.anyValue(), AccountTestHelper.anyValue())

        val result = service.signUp(" Manager@Company.co.kr ", "password1", "124-81-00998")

        val order = inOrder(repository, biznoBusinessService)
        order.verify(repository).findByEmail("manager@company.co.kr")
        order.verify(biznoBusinessService).findByBusinessNumber("124-81-00998")
        order.verify(repository).createAccount(AccountTestHelper.anyValue(), AccountTestHelper.anyValue())

        val newAccount = requireNotNull(storedAccount)
        val session = requireNotNull(storedSession)
        assertEquals("manager@company.co.kr", newAccount.email)
        assertTrue(passwordEncoder.matches("password1", newAccount.passwordHash))
        assertEquals(LocalDateTime.of(2026, 9, 6, 12, 0), newAccount.termsAgreedAt)
        assertEquals("1248100998", newAccount.company.businessNumber)
        assertEquals("삼성전자(주)", newAccount.company.companyName)
        assertEquals("계속사업자", newAccount.company.businessStatus)
        assertEquals(LocalDateTime.of(2026, 9, 6, 12, 0), newAccount.company.verifiedAt)
        assertEquals(SessionTokenHelper.hash(result.sessionToken), session.tokenHash)
        assertEquals(LocalDateTime.of(2026, 10, 6, 12, 0), session.expiresAt)
        assertEquals(account, result.account)
    }

    @Test
    fun rejectsAnAlreadyRegisteredEmailBeforeCallingBizno() {
        doReturn(AccountTestHelper.account()).`when`(repository).findByEmail("manager@company.co.kr")

        assertThrows(EmailAlreadyRegisteredException::class.java) {
            service.signUp("manager@company.co.kr", "password1", "1248100998")
        }

        verifyNoInteractions(biznoBusinessService)
        verify(repository, never()).createAccount(AccountTestHelper.anyValue(), AccountTestHelper.anyValue())
    }

    @Test
    fun rejectsABusinessNumberThatBiznoDoesNotConfirm() {
        doReturn(emptyList<BiznoBusiness>()).`when`(biznoBusinessService).findByBusinessNumber("1234567890")

        assertThrows(BusinessNotFoundException::class.java) {
            service.signUp("manager@company.co.kr", "password1", "1234567890")
        }

        verify(repository, never()).createAccount(AccountTestHelper.anyValue(), AccountTestHelper.anyValue())
    }

    @Test
    fun mapsADatabaseUniqueViolationToTheSameConflictAsThePreCheck() {
        doReturn(listOf(BiznoBusiness("1248100998", "삼성전자(주)", "계속사업자")))
            .`when`(biznoBusinessService).findByBusinessNumber("1248100998")
        doThrow(DuplicateKeyException("uq_account_email"))
            .`when`(repository).createAccount(AccountTestHelper.anyValue(), AccountTestHelper.anyValue())

        assertThrows(EmailAlreadyRegisteredException::class.java) {
            service.signUp("manager@company.co.kr", "password1", "1248100998")
        }
    }
}
