package ai.govbiz.core.account.controller

import ai.govbiz.core._common.exception.ApiExceptionHandler
import ai.govbiz.core.account.client.bizno.exception.BiznoClientException
import ai.govbiz.core.account.helper.AccountTestHelper
import ai.govbiz.core.account.service.AccountLoginService
import ai.govbiz.core.account.service.AccountSessionService
import ai.govbiz.core.account.service.AccountSignupService
import ai.govbiz.core.account.service.dto.AccountSessionResult
import ai.govbiz.core.account.service.exception.AuthenticationRequiredException
import ai.govbiz.core.account.service.exception.BusinessNotFoundException
import ai.govbiz.core.account.service.exception.EmailAlreadyRegisteredException
import ai.govbiz.core.account.service.exception.InvalidCredentialsException
import java.time.OffsetDateTime
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@ExtendWith(MockitoExtension::class)
class AccountAuthControllerTest {

    @Mock
    private lateinit var signupService: AccountSignupService

    @Mock
    private lateinit var loginService: AccountLoginService

    @Mock
    private lateinit var sessionService: AccountSessionService

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(AccountAuthController(signupService, loginService, sessionService))
            .setControllerAdvice(ApiExceptionHandler())
            .build()
    }

    @Test
    fun signUpReturnsTheSessionContractWithA201() {
        doReturn(sessionResult()).`when`(signupService).signUp("manager@company.co.kr", "password1", "124-81-00998")

        mockMvc.perform(
            post(SIGNUP_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"manager@company.co.kr","password":"password1","businessNumber":"124-81-00998"}"""),
        )
            .andExpect(status().isCreated())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.sessionToken").value("session-token"))
            .andExpect(jsonPath("$.expiresAt").value("2026-10-06T12:00:00+09:00"))
            .andExpect(jsonPath("$.account.email").value("manager@company.co.kr"))
            .andExpect(jsonPath("$.account.company.businessNumber").value("1248100998"))
            .andExpect(jsonPath("$.account.company.companyName").value("삼성전자(주)"))
            .andExpect(jsonPath("$.account.company.businessStatus").value("계속사업자"))
            .andExpect(content().string(not(containsString("password"))))
    }

    @ParameterizedTest
    @CsvSource(
        "not-an-email, password1, 1248100998, email",
        "manager@company.co.kr, short1, 1248100998, password",
        "manager@company.co.kr, passwordonly, 1248100998, password",
        "manager@company.co.kr, 12345678, 1248100998, password",
        "manager@company.co.kr, password1, 123456789, businessNumber",
        "'', password1, 1248100998, email",
    )
    fun signUpRejectsInvalidFieldsBeforeReachingTheService(
        email: String,
        password: String,
        businessNumber: String,
        expectedField: String,
    ) {
        mockMvc.perform(
            post(SIGNUP_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"$password","businessNumber":"$businessNumber"}"""),
        )
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors[0].field").value(expectedField))
            .andExpect(content().string(not(containsString(password))))

        verifyNoInteractions(signupService)
    }

    @Test
    fun signUpMapsConflictBusinessAndBiznoFailuresToStableProblems() {
        doThrow(EmailAlreadyRegisteredException()).`when`(signupService).signUp("dup@company.co.kr", "password1", "1248100998")
        doThrow(BusinessNotFoundException()).`when`(signupService).signUp("new@company.co.kr", "password1", "1234567890")
        doThrow(BiznoClientException.notConfigured()).`when`(signupService).signUp("bizno@company.co.kr", "password1", "1248100998")

        signUp("dup@company.co.kr", "1248100998")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
            .andExpect(jsonPath("$.type").value("urn:govbiz:problem:email-already-registered"))

        signUp("new@company.co.kr", "1234567890")
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.code").value("BUSINESS_NOT_FOUND"))

        signUp("bizno@company.co.kr", "1248100998")
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("BIZNO_NOT_CONFIGURED"))
    }

    @Test
    fun logInReturnsTheSessionContractOrASingleInvalidCredentialsProblem() {
        doReturn(sessionResult()).`when`(loginService).logIn("manager@company.co.kr", "password1")
        doThrow(InvalidCredentialsException()).`when`(loginService).logIn("manager@company.co.kr", "wrong")

        mockMvc.perform(
            post(LOGIN_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"manager@company.co.kr","password":"password1"}"""),
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessionToken").value("session-token"))
            .andExpect(jsonPath("$.account.company.companyName").value("삼성전자(주)"))

        mockMvc.perform(
            post(LOGIN_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"manager@company.co.kr","password":"wrong"}"""),
        )
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
            .andExpect(jsonPath("$.detail").value("The email or password is incorrect."))
    }

    @Test
    fun logInRejectsAMissingPasswordBeforeReachingTheService() {
        mockMvc.perform(
            post(LOGIN_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"manager@company.co.kr","password":""}"""),
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors[0].field").value("password"))

        verifyNoInteractions(loginService)
    }

    @Test
    fun meReturnsTheAccountForAValidBearerToken() {
        doReturn(AccountTestHelper.account()).`when`(sessionService).requireAccount("Bearer session-token")

        mockMvc.perform(get(ME_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer session-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.account.email").value("manager@company.co.kr"))
            .andExpect(jsonPath("$.account.company.businessNumber").value("1248100998"))
    }

    @Test
    fun meAndLogoutAnswer401WithABearerChallengeWhenTheSessionIsMissing() {
        doThrow(AuthenticationRequiredException()).`when`(sessionService).requireAccount(null)
        doThrow(AuthenticationRequiredException()).`when`(sessionService).logOut(null)

        mockMvc.perform(get(ME_PATH))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
            .andExpect(jsonPath("$.type").value("urn:govbiz:problem:authentication-required"))

        mockMvc.perform(post(LOGOUT_PATH))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
    }

    @Test
    fun logOutAnswers204WithoutABody() {
        mockMvc.perform(post(LOGOUT_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer session-token"))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""))
    }

    private fun signUp(email: String, businessNumber: String) =
        mockMvc.perform(
            post(SIGNUP_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"password1","businessNumber":"$businessNumber"}"""),
        )

    private fun sessionResult() =
        AccountSessionResult(
            sessionToken = "session-token",
            expiresAt = OffsetDateTime.parse("2026-10-06T12:00:00+09:00"),
            account = AccountTestHelper.account(),
        )

    private companion object {
        const val SIGNUP_PATH = "/api/v1/auth/signup"
        const val LOGIN_PATH = "/api/v1/auth/login"
        const val LOGOUT_PATH = "/api/v1/auth/logout"
        const val ME_PATH = "/api/v1/auth/me"
    }
}
