package ai.govbiz.core.account.admin

import ai.govbiz.core.account.domain.AccountPage
import ai.govbiz.core.account.domain.AccountRole
import ai.govbiz.core.account.helper.AccountTestHelper
import ai.govbiz.core.account.repository.AccountRepository
import ai.govbiz.core.account.service.exception.AccountNotFoundException
import ai.govbiz.core.account.service.exception.AdminRequiredException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class AdminAccountServiceTest {

    @Mock
    private lateinit var repository: AccountRepository

    private lateinit var service: AdminAccountService

    @BeforeEach
    fun setUp() {
        service = AdminAccountService(repository)
    }

    @Test
    fun listsAccountsForAnAdministrator() {
        val admin = AccountTestHelper.account(id = 9L, email = "admin@govbiz.test", role = AccountRole.ADMIN)
        val page = AccountPage(listOf(AccountTestHelper.account()), 0, 20, 1)
        doReturn(page).`when`(repository).findPage("manager", 0, 20)

        assertEquals(page, service.listAccounts(admin, "manager", 0, 20))
    }

    @Test
    fun rejectsNonAdministratorsBeforeTouchingTheRepository() {
        val user = AccountTestHelper.account()

        assertThrows(AdminRequiredException::class.java) { service.listAccounts(user, null, 0, 20) }
        assertThrows(AdminRequiredException::class.java) { service.revokeSessions(user, 2L) }

        verifyNoInteractions(repository)
    }

    @Test
    fun revokesSessionsOfAnExistingAccountAndReports404Otherwise() {
        val admin = AccountTestHelper.account(id = 9L, email = "admin@govbiz.test", role = AccountRole.ADMIN)
        doReturn(AccountTestHelper.account(id = 2L)).`when`(repository).findById(2L)
        doReturn(3).`when`(repository).deleteSessionsByAccountId(2L)

        service.revokeSessions(admin, 2L)
        verify(repository).deleteSessionsByAccountId(2L)

        assertThrows(AccountNotFoundException::class.java) { service.revokeSessions(admin, 404L) }
        verify(repository, never()).deleteSessionsByAccountId(404L)
    }
}
