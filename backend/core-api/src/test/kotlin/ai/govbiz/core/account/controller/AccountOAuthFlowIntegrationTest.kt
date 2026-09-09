package ai.govbiz.core.account.controller

import ai.govbiz.core._common.test.MySqlTestContainerConfig
import ai.govbiz.core.account.client.oauth.GoogleOAuthClient
import ai.govbiz.core.account.client.oauth.KakaoOAuthClient
import ai.govbiz.core.account.client.oauth.OAuthCodeExchange
import ai.govbiz.core.account.client.oauth.OAuthProviderClient
import ai.govbiz.core.account.client.oauth.OAuthProviderClientTest
import ai.govbiz.core.account.client.oauth.OAuthProviderClients
import ai.govbiz.core.account.domain.NewAccount
import ai.govbiz.core.account.domain.OAuthIdentity
import ai.govbiz.core.account.domain.OAuthProvider
import ai.govbiz.core.account.helper.OAuthStateCookieHelper
import ai.govbiz.core.account.helper.SessionCookieHelper
import ai.govbiz.core.account.repository.AccountRepository
import jakarta.servlet.http.Cookie
import java.net.URI
import java.net.URLDecoder
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder

/**
 * 실제 MySQL과 HTTP 계층으로 소셜 로그인 시작 → 콜백 → 세션 → 재로그인·기존 계정 연결·state 불일치를 확인합니다.
 * 제공처 호출만 고정 결과로 바꿉니다.
 */
@SpringBootTest(
    properties = [
        "app.account.jwt-secret=test-jwt-secret-0123456789abcdef0123456789",
        "app.ai-service.base-url=http://127.0.0.1:1",
        "app.ai-service.connect-timeout=10ms",
        "app.ai-service.read-timeout=10ms",
        "app.bizinfo.sync.enabled=false",
        "app.support-program-index.enabled=false",
        "app.account.cookie-secure=false",
        "app.oauth.redirect-base-url=http://127.0.0.1:5173",
        "app.oauth.frontend-base-url=http://127.0.0.1:5173",
    ],
)
@AutoConfigureMockMvc
@Import(MySqlTestContainerConfig::class)
class AccountOAuthFlowIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var repository: AccountRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @MockitoBean
    private lateinit var clients: OAuthProviderClients

    /** 콜백에서 제공처가 돌려줄 사용자입니다. 테스트가 바꿔 가며 씁니다. */
    private var googleIdentity: OAuthIdentity = OAuthIdentity(OAuthProvider.GOOGLE, "sub-1", "Social@Company.co.kr".lowercase(), true)

    @BeforeEach
    fun resetAccountsAndProviders() {
        jdbcTemplate.update("DELETE FROM account_session")
        jdbcTemplate.update("DELETE FROM account_social_identity")
        jdbcTemplate.update("DELETE FROM account")

        val properties = OAuthProviderClientTest.properties(kakaoClientId = "")
        val fakeGoogle = object : OAuthProviderClient(OAuthProvider.GOOGLE, RestClient.builder().build(), properties.google) {
            override fun authorizationUrl(state: String, redirectUri: URI, codeChallenge: String?): URI =
                UriComponentsBuilder.fromUri(URI.create("https://accounts.example/o/oauth2/v2/auth"))
                    .queryParam("state", state).queryParam("redirect_uri", redirectUri.toString()).build().encode().toUri()
            override fun exchangeCode(exchange: OAuthCodeExchange): String = "token"
            override fun fetchUserInfo(accessToken: String): OAuthIdentity = googleIdentity
        }
        doReturn(listOf(OAuthProvider.GOOGLE)).`when`(clients).enabledProviders
        doReturn(fakeGoogle).`when`(clients).client(OAuthProvider.GOOGLE)
        doReturn(KakaoOAuthClient(RestClient.builder().build(), properties)).`when`(clients).client(OAuthProvider.KAKAO)
    }

    @Test
    fun startsWithASignedStateCookieCompletesIntoASessionAndReusesTheAccountOnTheNextLogin() {
        mockMvc.perform(get("/api/v1/auth/oauth/providers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.providers[0]").value("google"))
            .andExpect(jsonPath("$.providers.length()").value(1))

        val start = mockMvc.perform(get("/api/v1/auth/oauth/google/start").param("next", "/app/partners"))
            .andExpect(status().isFound())
            .andExpect(cookie().httpOnly(OAuthStateCookieHelper.COOKIE_NAME, true))
            .andExpect(cookie().path(OAuthStateCookieHelper.COOKIE_NAME, "/api/v1/auth/oauth"))
            .andReturn()
        val location = requireNotNull(start.response.getHeader(HttpHeaders.LOCATION))
        assertTrue(location.startsWith("https://accounts.example/o/oauth2/v2/auth?"))
        val query = queryOf(location)
        assertEquals("http://127.0.0.1:5173/api/v1/auth/oauth/google/callback", query["redirect_uri"])
        val stateCookie = requireNotNull(start.response.getCookie(OAuthStateCookieHelper.COOKIE_NAME))

        val callback = mockMvc.perform(
            get("/api/v1/auth/oauth/google/callback").param("code", "code-1").param("state", requireNotNull(query["state"])).cookie(stateCookie),
        )
            .andExpect(status().isFound())
            .andExpect(header().string(HttpHeaders.LOCATION, "http://127.0.0.1:5173/oauth/callback?next=%2Fapp%2Fpartners"))
            .andExpect(cookie().httpOnly(SessionCookieHelper.COOKIE_NAME, true))
            .andExpect(cookie().maxAge(SessionCookieHelper.COOKIE_NAME, 30 * 24 * 60 * 60))
            .andExpect(cookie().maxAge(OAuthStateCookieHelper.COOKIE_NAME, 0))
            .andReturn()
        val session = requireNotNull(callback.response.getCookie(SessionCookieHelper.COOKIE_NAME))

        mockMvc.perform(get("/api/v1/auth/me").cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.account.email").value("social@company.co.kr"))
            .andExpect(jsonPath("$.account.tier").value("MEMBER"))
            .andExpect(jsonPath("$.account.emailVerified").value(true))
        assertNull(jdbcTemplate.queryForObject("SELECT password_hash FROM account WHERE email = 'social@company.co.kr'", String::class.java))
        assertEquals(
            1,
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_social_identity WHERE provider = 'GOOGLE' AND provider_user_id = 'sub-1' AND email = 'social@company.co.kr'",
                Int::class.java,
            ),
        )

        // 비밀번호가 없는 계정은 이메일 로그인이 되지 않습니다.
        mockMvc.perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"social@company.co.kr","password":"anything-1"}"""),
        )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))

        // 같은 Google 계정으로 다시 로그인하면 새 계정을 만들지 않습니다.
        val secondSession = logInWithGoogle(next = null)
        mockMvc.perform(get("/api/v1/auth/me").cookie(secondSession))
            .andExpect(jsonPath("$.account.email").value("social@company.co.kr"))
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM account", Int::class.java))
    }

    @Test
    fun linksAVerifiedProviderEmailToTheExistingPasswordAccountAndRejectsStateMismatchAndDisabledProviders() {
        repository.createAccount(
            NewAccount(
                email = "manager@company.co.kr",
                passwordHash = requireNotNull(passwordEncoder.encode("password1")),
                termsAgreedAt = LocalDateTime.of(2026, 9, 6, 12, 0),
            ),
        )
        googleIdentity = OAuthIdentity(OAuthProvider.GOOGLE, "sub-2", "manager@company.co.kr", true)

        val session = logInWithGoogle(next = "/app/profile")
        mockMvc.perform(get("/api/v1/auth/me").cookie(session))
            .andExpect(jsonPath("$.account.email").value("manager@company.co.kr"))
            .andExpect(jsonPath("$.account.emailVerified").value(true))
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM account", Int::class.java))
        assertNotNull(jdbcTemplate.queryForObject("SELECT password_hash FROM account WHERE email = 'manager@company.co.kr'", String::class.java))

        // 비밀번호 로그인은 그대로 됩니다.
        mockMvc.perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"manager@company.co.kr","password":"password1"}"""),
        ).andExpect(status().isOk())

        // 다른 브라우저의 state 쿠키 없이 돌아온 콜백은 세션 없이 오류로 돌려보냅니다.
        mockMvc.perform(get("/api/v1/auth/oauth/google/callback").param("code", "code-9").param("state", "forged"))
            .andExpect(status().isFound())
            .andExpect(header().string(HttpHeaders.LOCATION, "http://127.0.0.1:5173/oauth/callback?error=STATE_MISMATCH"))
            .andExpect(cookie().doesNotExist(SessionCookieHelper.COOKIE_NAME))

        // 설정되지 않은 제공처는 시작 단계에서 오류로 돌려보내고, 모르는 제공처는 404입니다.
        mockMvc.perform(get("/api/v1/auth/oauth/kakao/start"))
            .andExpect(status().isFound())
            .andExpect(header().string(HttpHeaders.LOCATION, "http://127.0.0.1:5173/oauth/callback?error=PROVIDER_NOT_CONFIGURED"))
        mockMvc.perform(get("/api/v1/auth/oauth/naver/start")).andExpect(status().isNotFound())

        // 제공처가 이메일을 주지 않으면 계정을 만들지 않습니다.
        googleIdentity = OAuthIdentity(OAuthProvider.GOOGLE, "sub-3", null, false)
        val denied = startAndCallback(next = null, providerError = null)
        assertEquals("http://127.0.0.1:5173/oauth/callback?error=EMAIL_REQUIRED", denied.response.getHeader(HttpHeaders.LOCATION))
        assertNull(denied.response.getCookie(SessionCookieHelper.COOKIE_NAME))

        // 동의 화면에서 취소하면 제공처가 error를 돌려주고 세션은 없습니다.
        val cancelled = startAndCallback(next = null, providerError = "access_denied")
        assertEquals("http://127.0.0.1:5173/oauth/callback?error=PROVIDER_DENIED", cancelled.response.getHeader(HttpHeaders.LOCATION))
    }

    private fun logInWithGoogle(next: String?): Cookie {
        val result = startAndCallback(next, providerError = null)
        assertEquals(302, result.response.status)
        return requireNotNull(result.response.getCookie(SessionCookieHelper.COOKIE_NAME)) { "session cookie was not issued" }
    }

    private fun startAndCallback(next: String?, providerError: String?): MvcResult {
        val start = mockMvc.perform(get("/api/v1/auth/oauth/google/start").apply { if (next != null) param("next", next) })
            .andExpect(status().isFound())
            .andReturn()
        val state = requireNotNull(queryOf(requireNotNull(start.response.getHeader(HttpHeaders.LOCATION)))["state"])
        val stateCookie = requireNotNull(start.response.getCookie(OAuthStateCookieHelper.COOKIE_NAME))
        val request = get("/api/v1/auth/oauth/google/callback").param("state", state).cookie(stateCookie)
        if (providerError == null) request.param("code", "code-x") else request.param("error", providerError)
        return mockMvc.perform(request).andReturn()
    }

    private fun queryOf(url: String): Map<String, String> =
        UriComponentsBuilder.fromUriString(url).build().queryParams
            .mapValues { (_, values) -> URLDecoder.decode(values.first(), Charsets.UTF_8) }
}
