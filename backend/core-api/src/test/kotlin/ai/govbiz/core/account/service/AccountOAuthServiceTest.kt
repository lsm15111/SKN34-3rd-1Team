package ai.govbiz.core.account.service

import ai.govbiz.core.account.client.oauth.GoogleOAuthClient
import ai.govbiz.core.account.client.oauth.KakaoOAuthClient
import ai.govbiz.core.account.client.oauth.OAuthCodeExchange
import ai.govbiz.core.account.client.oauth.OAuthProviderClient
import ai.govbiz.core.account.client.oauth.OAuthProviderClientTest
import ai.govbiz.core.account.client.oauth.OAuthProviderClients
import ai.govbiz.core.account.domain.NewAccount
import ai.govbiz.core.account.domain.NewSocialIdentity
import ai.govbiz.core.account.domain.OAuthIdentity
import ai.govbiz.core.account.domain.OAuthProvider
import ai.govbiz.core.account.domain.SocialIdentity
import ai.govbiz.core.account.helper.AccountTestHelper
import ai.govbiz.core.account.helper.AccountTestHelper.NOW
import ai.govbiz.core.account.helper.OAuthLoginState
import ai.govbiz.core.account.helper.OAuthStateHelper
import ai.govbiz.core.account.repository.AccountRepository
import ai.govbiz.core.account.service.exception.AccountSuspendedException
import ai.govbiz.core.account.service.exception.OAuthLoginFailedException
import ai.govbiz.core.account.service.exception.OAuthLoginFailedException.Code
import java.net.URI
import java.net.URLDecoder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.dao.DuplicateKeyException
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder

@ExtendWith(MockitoExtension::class)
class AccountOAuthServiceTest {

    @Mock
    private lateinit var repository: AccountRepository

    private lateinit var service: AccountOAuthService

    @BeforeEach
    fun setUp() {
        val properties = OAuthProviderClientTest.properties()
        val restClient = RestClient.builder().build()
        service = AccountOAuthService(
            repository,
            AccountSessionService(repository, AccountTestHelper.sessionProperties(), AccountTestHelper.FIXED_CLOCK),
            OAuthProviderClients(
                listOf<OAuthProviderClient>(
                    GoogleOAuthClient(restClient, properties),
                    KakaoOAuthClient(restClient, properties),
                ),
            ),
            properties,
            AccountLoginAttemptGuard(AccountTestHelper.FIXED_CLOCK),
            AccountTestHelper.FIXED_CLOCK,
        )
    }

    @Test
    fun startBuildsAConsentUrlWithASignedStateAndKeepsOnlyInAppReturnPaths() {
        val started = service.start(OAuthProvider.GOOGLE, "/app/partners")

        assertEquals(OAuthProvider.GOOGLE, started.state.provider)
        assertEquals("/app/partners", started.state.next)
        assertNotNull(started.state.codeVerifier)
        assertEquals(AccountTestHelper.FIXED_CLOCK.instant().plus(AccountOAuthService.STATE_TTL), started.state.expiresAt)
        val query = UriComponentsBuilder.fromUri(started.authorizationUrl).build().queryParams
            .mapValues { (_, values) -> URLDecoder.decode(values.first(), Charsets.UTF_8) }
        assertEquals(started.state.state, query["state"])
        assertEquals("http://127.0.0.1:5173/api/v1/auth/oauth/google/callback", query["redirect_uri"])
        assertEquals(OAuthStateHelper.codeChallenge(requireNotNull(started.state.codeVerifier)), query["code_challenge"])

        assertEquals(AccountOAuthService.DEFAULT_NEXT, service.start(OAuthProvider.KAKAO, "https://evil.example").state.next)
        assertEquals(AccountOAuthService.DEFAULT_NEXT, service.start(OAuthProvider.KAKAO, "//evil.example").state.next)
        assertNull(service.start(OAuthProvider.KAKAO, null).state.codeVerifier)
        assertEquals(URI.create("http://127.0.0.1:5173/oauth/callback?next=%2Fapp%2Fpartners"), service.frontendCallbackUri("/app/partners", null))
        assertEquals(URI.create("http://127.0.0.1:5173/oauth/callback?error=EMAIL_REQUIRED"), service.frontendCallbackUri(null, Code.EMAIL_REQUIRED))
    }

    @Test
    fun completeRejectsMissingOrForeignStateAndProviderCancellationBeforeCallingTheProvider() {
        val stored = OAuthLoginState(OAuthProvider.GOOGLE, "state-1", "verifier", "/app/chat", AccountTestHelper.FIXED_CLOCK.instant().plusSeconds(60))

        assertEquals(Code.STATE_MISMATCH, failure { service.complete(OAuthProvider.GOOGLE, null, "state-1", "code", null, "10.0.0.1") })
        assertEquals(Code.STATE_MISMATCH, failure { service.complete(OAuthProvider.GOOGLE, stored, "other", "code", null, "10.0.0.1") })
        assertEquals(Code.STATE_MISMATCH, failure { service.complete(OAuthProvider.KAKAO, stored, "state-1", "code", null, "10.0.0.1") })
        assertEquals(Code.PROVIDER_DENIED, failure { service.complete(OAuthProvider.GOOGLE, stored, "state-1", null, "access_denied", "10.0.0.1") })
        assertEquals(Code.PROVIDER_DENIED, failure { service.complete(OAuthProvider.GOOGLE, stored, "state-1", null, null, "10.0.0.1") })
        verify(repository, never()).findSocialIdentity(AccountTestHelper.anyValue(), AccountTestHelper.anyValue())
    }

    @Test
    fun signInReusesTheAccountLinkedToTheProviderUserWithoutTouchingEmails() {
        val account = AccountTestHelper.account(id = 5L, email = "manager@company.co.kr")
        doReturn(SocialIdentity(1L, 5L, OAuthProvider.GOOGLE, "sub-1", "old@company.co.kr", NOW))
            .`when`(repository).findSocialIdentity(OAuthProvider.GOOGLE, "sub-1")
        doReturn(account).`when`(repository).findById(5L)

        assertEquals(account, service.signIn(OAuthIdentity(OAuthProvider.GOOGLE, "sub-1", "new@company.co.kr", true)))
        verify(repository, never()).findByEmail(AccountTestHelper.anyValue())
        verify(repository, never()).createAccount(AccountTestHelper.anyValue())
    }

    @Test
    fun signInCreatesAPasswordlessVerifiedAccountForANewProviderUser() {
        var created: NewAccount? = null
        var linked: NewSocialIdentity? = null
        doReturn(null).`when`(repository).findSocialIdentity(OAuthProvider.KAKAO, "77")
        doReturn(null).`when`(repository).findByEmail("user@kakao.test")
        doAnswer { invocation ->
            created = invocation.getArgument(0)
            AccountTestHelper.account(id = 9L, email = "user@kakao.test", emailVerifiedAt = NOW)
        }.`when`(repository).createAccount(AccountTestHelper.anyValue())
        doAnswer { invocation ->
            linked = invocation.getArgument(0)
            SocialIdentity(3L, 9L, OAuthProvider.KAKAO, "77", "user@kakao.test", NOW)
        }.`when`(repository).linkSocialIdentity(AccountTestHelper.anyValue())
        doReturn(AccountTestHelper.account(id = 9L, email = "user@kakao.test", emailVerifiedAt = NOW)).`when`(repository).findById(9L)

        val account = service.signIn(OAuthIdentity(OAuthProvider.KAKAO, "77", "user@kakao.test", true))

        assertEquals(9L, account.id)
        val newAccount = requireNotNull(created)
        assertNull(newAccount.passwordHash)
        assertEquals(NOW, newAccount.termsAgreedAt)
        assertEquals(NOW, newAccount.emailVerifiedAt)
        assertEquals(NewSocialIdentity(9L, OAuthProvider.KAKAO, "77", "user@kakao.test", NOW), linked)
        verify(repository, never()).markEmailVerified(anyLong(), AccountTestHelper.anyValue())
    }

    @Test
    fun signInLinksAVerifiedProviderEmailToTheExistingAccountAndMarksItVerified() {
        val existing = AccountTestHelper.account(id = 4L, email = "manager@company.co.kr")
        doReturn(null).`when`(repository).findSocialIdentity(OAuthProvider.GOOGLE, "sub-2")
        doReturn(existing).`when`(repository).findByEmail("manager@company.co.kr")
        doReturn(SocialIdentity(8L, 4L, OAuthProvider.GOOGLE, "sub-2", "manager@company.co.kr", NOW))
            .`when`(repository).linkSocialIdentity(AccountTestHelper.anyValue())
        doReturn(existing.copy(emailVerifiedAt = NOW)).`when`(repository).findById(4L)

        val account = service.signIn(OAuthIdentity(OAuthProvider.GOOGLE, "sub-2", "manager@company.co.kr", true))

        assertTrue(account.isEmailVerified)
        verify(repository).markEmailVerified(4L, NOW)
        verify(repository, never()).createAccount(AccountTestHelper.anyValue())
    }

    @Test
    fun signInRefusesToLinkAnUnverifiedEmailToAnExistingAccountAndRequiresAnEmailForNewOnes() {
        doReturn(null).`when`(repository).findSocialIdentity(OAuthProvider.KAKAO, "77")
        doReturn(AccountTestHelper.account(id = 4L, email = "manager@company.co.kr")).`when`(repository).findByEmail("manager@company.co.kr")

        assertEquals(Code.EMAIL_NOT_VERIFIED, failure { service.signIn(OAuthIdentity(OAuthProvider.KAKAO, "77", "manager@company.co.kr", false)) })
        assertEquals(Code.EMAIL_REQUIRED, failure { service.signIn(OAuthIdentity(OAuthProvider.KAKAO, "77", null, false)) })
        verify(repository, never()).linkSocialIdentity(AccountTestHelper.anyValue())
        verify(repository, never()).createAccount(AccountTestHelper.anyValue())
    }

    @Test
    fun signInFollowsTheWinnerWhenAConcurrentRequestLinkedTheSameProviderUserFirst() {
        val winner = AccountTestHelper.account(id = 6L, email = "user@kakao.test")
        doReturn(null, SocialIdentity(2L, 6L, OAuthProvider.KAKAO, "77", "user@kakao.test", NOW))
            .`when`(repository).findSocialIdentity(OAuthProvider.KAKAO, "77")
        doReturn(null).`when`(repository).findByEmail("user@kakao.test")
        doReturn(winner).`when`(repository).createAccount(AccountTestHelper.anyValue())
        doAnswer { throw DuplicateKeyException("uq_account_social_identity_provider_user") }
            .`when`(repository).linkSocialIdentity(AccountTestHelper.anyValue())
        doReturn(winner).`when`(repository).findById(6L)

        assertEquals(winner, service.signIn(OAuthIdentity(OAuthProvider.KAKAO, "77", "user@kakao.test", true)))
    }

    @Test
    fun completeIssuesALongSessionAndBlocksSuspendedAccounts() {
        val stored = OAuthLoginState(OAuthProvider.GOOGLE, "state-1", "verifier", "/app/chat", AccountTestHelper.FIXED_CLOCK.instant().plusSeconds(60))
        doReturn(SocialIdentity(1L, 5L, OAuthProvider.GOOGLE, "sub-1", null, NOW))
            .`when`(repository).findSocialIdentity(OAuthProvider.GOOGLE, "sub-1")
        doReturn(AccountTestHelper.account(id = 5L, suspendedAt = NOW)).`when`(repository).findById(5L)
        val suspendedService = serviceWithIdentity(OAuthIdentity(OAuthProvider.GOOGLE, "sub-1", null, false))

        assertThrows(AccountSuspendedException::class.java) {
            suspendedService.complete(OAuthProvider.GOOGLE, stored, "state-1", "code", null, "10.0.0.1")
        }
        verify(repository, never()).createSession(anyLong(), AccountTestHelper.anyValue())
    }

    /** 제공처 호출만 고정 결과로 바꾼 Service입니다. */
    private fun serviceWithIdentity(identity: OAuthIdentity): AccountOAuthService {
        val properties = OAuthProviderClientTest.properties()
        val client = object : OAuthProviderClient(identity.provider, RestClient.builder().build(), properties.google) {
            override fun authorizationUrl(state: String, redirectUri: URI, codeChallenge: String?): URI = redirectUri
            override fun exchangeCode(exchange: OAuthCodeExchange): String = "token"
            override fun fetchUserInfo(accessToken: String): OAuthIdentity = identity
        }
        val other = KakaoOAuthClient(RestClient.builder().build(), properties)
        return AccountOAuthService(
            repository,
            AccountSessionService(repository, AccountTestHelper.sessionProperties(), AccountTestHelper.FIXED_CLOCK),
            OAuthProviderClients(listOf(client, other)),
            properties,
            AccountLoginAttemptGuard(AccountTestHelper.FIXED_CLOCK),
            AccountTestHelper.FIXED_CLOCK,
        )
    }

    private fun failure(block: () -> Unit): Code =
        assertThrows(OAuthLoginFailedException::class.java) { block() }.code
}
