package ai.govbiz.core.account.controller

import ai.govbiz.core._common.test.MySqlTestContainerConfig
import ai.govbiz.core.account.client.bizno.BiznoClient
import ai.govbiz.core.account.client.bizno.dto.BiznoBusiness
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper

/** 실제 MySQL과 HTTP 계층을 통해 가입 → 내 정보 → 로그아웃 → 로그인 흐름을 확인합니다. Bizno만 대역입니다. */
@SpringBootTest(
    properties = [
        "app.ai-service.base-url=http://127.0.0.1:1",
        "app.ai-service.connect-timeout=10ms",
        "app.ai-service.read-timeout=10ms",
        "app.bizinfo.sync.enabled=false",
        "app.support-program-index.enabled=false",
    ],
)
@AutoConfigureMockMvc
@Import(MySqlTestContainerConfig::class)
class AccountAuthFlowIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var biznoClient: BiznoClient

    @BeforeEach
    fun resetAccounts() {
        jdbcTemplate.update("DELETE FROM account_session")
        jdbcTemplate.update("DELETE FROM account")
        jdbcTemplate.update("DELETE FROM company")
        doReturn(listOf(BiznoBusiness("1248100998", "삼성전자(주)", "계속사업자")))
            .`when`(biznoClient).findByBusinessNumber("1248100998")
        doReturn(emptyList<BiznoBusiness>()).`when`(biznoClient).findByBusinessNumber("1234567890")
    }

    @Test
    fun signsUpReadsTheCurrentAccountLogsOutAndLogsInAgain() {
        val signupBody = mockMvc.perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"Manager@Company.co.kr","password":"password1","businessNumber":"124-81-00998"}"""),
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.account.email").value("manager@company.co.kr"))
            .andExpect(jsonPath("$.account.role").value("USER"))
            .andExpect(jsonPath("$.account.company.companyName").value("삼성전자(주)"))
            .andReturn().response.contentAsString
        val sessionToken = objectMapper.readTree(signupBody).path("sessionToken").asString()

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer $sessionToken"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.account.email").value("manager@company.co.kr"))
            .andExpect(jsonPath("$.account.company.businessNumber").value("1248100998"))

        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer $sessionToken"))
            .andExpect(status().isNoContent())

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer $sessionToken"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))

        mockMvc.perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"manager@company.co.kr","password":"password1","businessNumber":"1248100998"}"""),
        )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))

        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"manager@company.co.kr","password":"wrong-password1"}"""),
        )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))

        val loginBody = mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"MANAGER@company.co.kr","password":"password1"}"""),
        )
            .andExpect(status().isOk())
            .andReturn().response.contentAsString
        val newToken = objectMapper.readTree(loginBody).path("sessionToken").asString()

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer $newToken"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.account.company.companyName").value("삼성전자(주)"))
    }

    @Test
    fun rejectsSignupWhenBiznoDoesNotConfirmTheBusiness() {
        mockMvc.perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"new@company.co.kr","password":"password1","businessNumber":"1234567890"}"""),
        )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.code").value("BUSINESS_NOT_FOUND"))

        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"new@company.co.kr","password":"password1"}"""),
        )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
    }
}
