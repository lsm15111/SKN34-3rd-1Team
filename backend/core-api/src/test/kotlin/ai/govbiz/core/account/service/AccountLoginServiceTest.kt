package ai.govbiz.core.account.service

import ai.govbiz.core.account.domain.AccountCredential
import ai.govbiz.core.account.domain.NewAccountSession
import ai.govbiz.core.account.helper.AccountTestHelper
import ai.govbiz.core.account.helper.SessionTokenHelper
import ai.govbiz.core.account.repository.AccountRepository
import ai.govbiz.core.account.service.exception.InvalidCredentialsException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.eq
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

@ExtendWith(MockitoExtension::class)
class AccountLoginServiceTest {

    @Mock
    private lateinit var repository: AccountRepository

    private val passwordEncoder = BCryptPasswordEncoder(4)

    private lateinit var service: AccountLoginService

    @BeforeEach
    fun setUp() {
        service = AccountLoginService(
            repository,
            AccountSessionService(repository, AccountTestHelper.sessionProperties(), AccountTestHelper.FIXED_CLOCK),
            passwordEncoder,
        )
    }

    @Test
    fun issuesAndStoresANewSessionWhenThePasswordMatches() {
        val account = AccountTestHelper.account(id = 7L)
        doReturn(AccountCredential(account, requireNotNull(passwordEncoder.encode("password1"))))
            .`when`(repository).findCredentialByEmail("manager@company.co.kr")
        var storedSession: NewAccountSession? = null
        doAnswer { invocation ->
            storedSession = invocation.getArgument(1)
            null
        }.`when`(repository).createSession(eq(7L), AccountTestHelper.anyValue())

        val result = service.logIn(" MANAGER@company.co.kr", "password1")

        val session = requireNotNull(storedSession)
        assertEquals(SessionTokenHelper.hash(result.sessionToken), session.tokenHash)
        assertEquals(account, result.account)
    }

    @Test
    fun rejectsAWrongPasswordWithoutCreatingASession() {
        doReturn(AccountCredential(AccountTestHelper.account(), requireNotNull(passwordEncoder.encode("password1"))))
            .`when`(repository).findCredentialByEmail("manager@company.co.kr")

        assertThrows(InvalidCredentialsException::class.java) {
            service.logIn("manager@company.co.kr", "password2")
        }

        verify(repository, never()).createSession(anyLong(), AccountTestHelper.anyValue())
    }

    @Test
    fun rejectsAnUnknownEmailWithTheSameErrorAsAWrongPassword() {
        assertThrows(InvalidCredentialsException::class.java) {
            service.logIn("unknown@company.co.kr", "password1")
        }

        verify(repository, never()).createSession(anyLong(), AccountTestHelper.anyValue())
    }
}
