package ai.govbiz.core.account.service

import ai.govbiz.core.account.helper.AccountTestHelper
import ai.govbiz.core.account.helper.SessionTokenHelper
import ai.govbiz.core.account.repository.AccountRepository
import ai.govbiz.core.account.service.exception.AuthenticationRequiredException
import java.time.LocalDateTime
import java.time.OffsetDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class AccountSessionServiceTest {

    @Mock
    private lateinit var repository: AccountRepository

    private lateinit var service: AccountSessionService

    @BeforeEach
    fun setUp() {
        service = AccountSessionService(
            repository,
            AccountTestHelper.sessionProperties(),
            AccountTestHelper.FIXED_CLOCK,
        )
    }

    @Test
    fun issuesARandomUrlSafeTokenWhoseHashAndExpiryAreStored() {
        val first = service.issue()
        val second = service.issue()

        assertTrue(Regex("[A-Za-z0-9_-]{43}").matches(first.sessionToken))
        assertNotEquals(first.sessionToken, second.sessionToken)
        assertEquals(SessionTokenHelper.hash(first.sessionToken), first.session.tokenHash)
        assertEquals(LocalDateTime.of(2026, 10, 6, 12, 0), first.session.expiresAt)
    }

    @Test
    fun formatsTheExpiryWithTheSeoulOffset() {
        val result = service.toResult(service.issue(), AccountTestHelper.account())

        assertEquals(OffsetDateTime.parse("2026-10-06T12:00:00+09:00"), result.expiresAt)
        assertEquals(AccountTestHelper.account(), result.account)
    }

    @Test
    fun resolvesTheAccountByTheHashOfTheBearerToken() {
        val account = AccountTestHelper.account()
        doReturn(account).`when`(repository).findAccountBySessionTokenHash(SessionTokenHelper.hash("token-1"))

        assertEquals(account, service.requireAccount("Bearer token-1"))
        assertEquals(account, service.requireAccount("bearer token-1"))
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["", "Bearer", "Bearer ", "Basic dXNlcjpwYXNz", "token-1"])
    fun rejectsMissingOrMalformedAuthorizationWithoutTouchingTheDatabase(authorization: String?) {
        assertThrows(AuthenticationRequiredException::class.java) {
            service.requireAccount(authorization)
        }
        assertThrows(AuthenticationRequiredException::class.java) {
            service.logOut(authorization)
        }

        verifyNoInteractions(repository)
    }

    @Test
    fun rejectsUnknownOrExpiredSessions() {
        assertThrows(AuthenticationRequiredException::class.java) {
            service.requireAccount("Bearer expired")
        }

        verify(repository).findAccountBySessionTokenHash(SessionTokenHelper.hash("expired"))
    }

    @Test
    fun logOutDeletesTheHashedSessionAndIgnoresMissingRows() {
        doReturn(false).`when`(repository).deleteSessionByTokenHash(SessionTokenHelper.hash("token-1"))

        service.logOut("Bearer token-1")

        verify(repository).deleteSessionByTokenHash(SessionTokenHelper.hash("token-1"))
    }
}
