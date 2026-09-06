package ai.govbiz.core.account.admin

import ai.govbiz.core._common.exception.ApiExceptionHandler
import ai.govbiz.core.account.domain.AccountPage
import ai.govbiz.core.account.domain.AccountRole
import ai.govbiz.core.account.helper.AccountTestHelper
import ai.govbiz.core.account.repository.AccountRepository
import ai.govbiz.core.account.service.AccountSessionService
import ai.govbiz.core.account.service.exception.AuthenticationRequiredException
import ai.govbiz.core.account.web.AuthenticatedAccountArgumentResolver
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@ExtendWith(MockitoExtension::class)
class AdminAccountControllerTest {

    @Mock
    private lateinit var repository: AccountRepository

    @Mock
    private lateinit var sessionService: AccountSessionService

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(AdminAccountController(AdminAccountService(repository)))
            .setCustomArgumentResolvers(AuthenticatedAccountArgumentResolver(sessionService))
            .setControllerAdvice(ApiExceptionHandler())
            .build()
    }

    @Test
    fun returnsThePagedAccountContractForAnAdministrator() {
        doReturn(admin()).`when`(sessionService).requireAccount(ADMIN_BEARER)
        doReturn(AccountPage(listOf(AccountTestHelper.account(id = 2L)), 0, 20, 1))
            .`when`(repository).findPage("manager", 0, 20)

        mockMvc.perform(get(PATH).queryParam("email", "manager").header(HttpHeaders.AUTHORIZATION, ADMIN_BEARER))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalCount").value(1))
            .andExpect(jsonPath("$.items[0].id").value(2))
            .andExpect(jsonPath("$.items[0].email").value("manager@company.co.kr"))
            .andExpect(jsonPath("$.items[0].role").value("USER"))
            .andExpect(jsonPath("$.items[0].company.companyName").value("삼성전자(주)"))
            .andExpect(jsonPath("$.items[0].createdAt").value("2026-09-06T12:00:00+09:00"))
    }

    @Test
    fun rejectsInvalidPagingBeforeTheService() {
        doReturn(admin()).`when`(sessionService).requireAccount(ADMIN_BEARER)

        mockMvc.perform(get(PATH).queryParam("size", "101").header(HttpHeaders.AUTHORIZATION, ADMIN_BEARER))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"))

        verify(repository, never()).findPage("", 0, 101)
    }

    @Test
    fun answers401WithoutASessionAnd403ForARegularUser() {
        doThrow(AuthenticationRequiredException()).`when`(sessionService).requireAccount(null)
        doReturn(AccountTestHelper.account()).`when`(sessionService).requireAccount(USER_BEARER)

        mockMvc.perform(get(PATH))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))

        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, USER_BEARER))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"))
            .andExpect(jsonPath("$.type").value("urn:govbiz:problem:admin-required"))

        mockMvc.perform(post("$PATH/2/sessions/revoke").header(HttpHeaders.AUTHORIZATION, USER_BEARER))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"))
    }

    @Test
    fun revokesSessionsWith204AndReports404ForAnUnknownAccount() {
        doReturn(admin()).`when`(sessionService).requireAccount(ADMIN_BEARER)
        doReturn(AccountTestHelper.account(id = 2L)).`when`(repository).findById(2L)
        doReturn(1).`when`(repository).deleteSessionsByAccountId(2L)

        mockMvc.perform(post("$PATH/2/sessions/revoke").header(HttpHeaders.AUTHORIZATION, ADMIN_BEARER))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""))

        mockMvc.perform(post("$PATH/404/sessions/revoke").header(HttpHeaders.AUTHORIZATION, ADMIN_BEARER))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"))
    }

    private fun admin() = AccountTestHelper.account(id = 9L, email = "admin@govbiz.test", role = AccountRole.ADMIN)

    private companion object {
        const val PATH = "/api/v1/admin/accounts"
        const val ADMIN_BEARER = "Bearer admin-token"
        const val USER_BEARER = "Bearer user-token"
    }
}
