package ai.govbiz.core.account.client.oauth

import ai.govbiz.core.account.client.oauth.config.OAuthClientProperties
import ai.govbiz.core.account.client.oauth.exception.OAuthClientException
import ai.govbiz.core.account.domain.OAuthIdentity
import ai.govbiz.core.account.domain.OAuthProvider
import java.net.URI
import java.net.URLDecoder
import java.time.Duration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder

class OAuthProviderClientTest {

    private lateinit var server: MockRestServiceServer
    private lateinit var google: GoogleOAuthClient
    private lateinit var kakao: KakaoOAuthClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        val restClient = builder.build()
        google = GoogleOAuthClient(restClient, properties())
        kakao = KakaoOAuthClient(restClient, properties())
    }

    @AfterEach
    fun verifiesEveryExpectedRequest() {
        server.verify()
    }

    @Test
    fun googleBuildsAPkceAuthorizationUrlAndReadsTheVerifiedEmailFromUserInfo() {
        val url = google.authorizationUrl("state-1", REDIRECT, "challenge-1")
        val query = queryOf(url)
        assertTrue(url.toString().startsWith("https://accounts.example/o/oauth2/v2/auth?"))
        assertEquals("code", query["response_type"])
        assertEquals("google-client", query["client_id"])
        assertEquals(REDIRECT.toString(), query["redirect_uri"])
        assertEquals("state-1", query["state"])
        assertEquals("openid email", query["scope"])
        assertEquals("challenge-1", query["code_challenge"])
        assertEquals("S256", query["code_challenge_method"])

        server.expect(requestTo("https://oauth2.example/token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE))
            .andExpect(content().string(
                "grant_type=authorization_code&code=code-1&client_id=google-client&client_secret=google-secret" +
                    "&redirect_uri=${java.net.URLEncoder.encode(REDIRECT.toString(), Charsets.UTF_8)}&code_verifier=verifier-1",
            ))
            .andRespond(withSuccess("""{"access_token":"at-1","token_type":"Bearer","id_token":"x"}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("https://openidconnect.example/v1/userinfo"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer at-1"))
            .andRespond(withSuccess("""{"sub":"10769150350006150715113082367","email":"Manager@Company.co.kr","email_verified":true}""", MediaType.APPLICATION_JSON))

        val identity = google.fetchIdentity(OAuthCodeExchange("code-1", REDIRECT, "verifier-1"))

        assertEquals(OAuthIdentity(OAuthProvider.GOOGLE, "10769150350006150715113082367", "manager@company.co.kr", true), identity)
    }

    @Test
    fun kakaoReadsTheNumericIdAndTreatsUnconsentedEmailAsAbsent() {
        val query = queryOf(kakao.authorizationUrl("state-2", REDIRECT, null))
        assertEquals("kakao-client", query["client_id"])
        assertEquals("account_email", query["scope"])
        assertFalse(query.containsKey("code_challenge"))

        server.expect(requestTo("https://kauth.example/oauth/token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(
                "grant_type=authorization_code&client_id=kakao-client" +
                    "&redirect_uri=${java.net.URLEncoder.encode(REDIRECT.toString(), Charsets.UTF_8)}&code=code-2",
            ))
            .andRespond(withSuccess("""{"access_token":"at-2","token_type":"bearer"}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("https://kapi.example/v2/user/me"))
            .andExpect(header("Authorization", "Bearer at-2"))
            .andRespond(withSuccess("""{"id":1234567890,"kakao_account":{"has_email":true,"email_needs_agreement":true}}""", MediaType.APPLICATION_JSON))

        assertEquals(OAuthIdentity(OAuthProvider.KAKAO, "1234567890", null, false), kakao.fetchIdentity(OAuthCodeExchange("code-2", REDIRECT, null)))
    }

    @Test
    fun kakaoRequiresBothVerifiedAndValidFlagsBeforeTrustingTheEmail() {
        server.expect(requestTo("https://kauth.example/oauth/token"))
            .andRespond(withSuccess("""{"access_token":"at-3"}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("https://kapi.example/v2/user/me"))
            .andRespond(withSuccess(
                """{"id":77,"kakao_account":{"email":"user@kakao.test","is_email_verified":true,"is_email_valid":false}}""",
                MediaType.APPLICATION_JSON,
            ))

        assertEquals(OAuthIdentity(OAuthProvider.KAKAO, "77", "user@kakao.test", false), kakao.fetchIdentity(OAuthCodeExchange("code-3", REDIRECT, null)))
    }

    @Test
    fun mapsProviderFailuresWithoutLeakingTokensAndRefusesWhenNotConfigured() {
        server.expect(requestTo("https://oauth2.example/token"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body("""{"error":"invalid_grant"}"""))
        val upstream = assertThrows(OAuthClientException::class.java) {
            google.fetchIdentity(OAuthCodeExchange("expired-code", REDIRECT, "verifier"))
        }
        assertEquals(OAuthClientException.Failure.UPSTREAM_ERROR, upstream.failure)
        assertFalse(upstream.message.orEmpty().contains("expired-code"))

        val disabled = KakaoOAuthClient(RestClient.builder().build(), properties(kakaoClientId = ""))
        assertFalse(disabled.isEnabled)
        assertEquals(
            OAuthClientException.Failure.NOT_CONFIGURED,
            assertThrows(OAuthClientException::class.java) { disabled.fetchIdentity(OAuthCodeExchange("code", REDIRECT, null)) }.failure,
        )
    }

    @Test
    fun treatsATokenResponseWithoutAnAccessTokenAsInvalid() {
        server.expect(requestTo("https://oauth2.example/token"))
            .andRespond(withSuccess("""{"token_type":"Bearer"}""", MediaType.APPLICATION_JSON))

        assertEquals(
            OAuthClientException.Failure.INVALID_RESPONSE,
            assertThrows(OAuthClientException::class.java) { google.fetchIdentity(OAuthCodeExchange("code", REDIRECT, "verifier")) }.failure,
        )
    }

    private fun queryOf(url: URI): Map<String, String> =
        UriComponentsBuilder.fromUri(url).build().queryParams
            .mapValues { (_, values) -> URLDecoder.decode(values.first(), Charsets.UTF_8) }

    companion object {
        val REDIRECT: URI = URI.create("http://127.0.0.1:5173/api/v1/auth/oauth/google/callback")

        fun properties(kakaoClientId: String = "kakao-client"): OAuthClientProperties =
            OAuthClientProperties(
                redirectBaseUrl = URI.create("http://127.0.0.1:5173"),
                frontendBaseUrl = URI.create("http://127.0.0.1:5173"),
                connectTimeout = Duration.ofSeconds(1),
                readTimeout = Duration.ofSeconds(1),
                google = OAuthClientProperties.Provider(
                    "google-client",
                    "google-secret",
                    URI.create("https://accounts.example/o/oauth2/v2/auth"),
                    URI.create("https://oauth2.example/token"),
                    URI.create("https://openidconnect.example/v1/userinfo"),
                ),
                kakao = OAuthClientProperties.Provider(
                    kakaoClientId,
                    "",
                    URI.create("https://kauth.example/oauth/authorize"),
                    URI.create("https://kauth.example/oauth/token"),
                    URI.create("https://kapi.example/v2/user/me"),
                ),
            )
    }
}
