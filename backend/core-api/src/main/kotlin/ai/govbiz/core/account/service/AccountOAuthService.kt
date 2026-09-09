package ai.govbiz.core.account.service

import ai.govbiz.core.account.client.oauth.OAuthCodeExchange
import ai.govbiz.core.account.client.oauth.OAuthProviderClients
import ai.govbiz.core.account.client.oauth.config.OAuthClientProperties
import ai.govbiz.core.account.client.oauth.exception.OAuthClientException
import ai.govbiz.core.account.domain.Account
import ai.govbiz.core.account.domain.NewAccount
import ai.govbiz.core.account.domain.NewSocialIdentity
import ai.govbiz.core.account.domain.OAuthIdentity
import ai.govbiz.core.account.domain.OAuthProvider
import ai.govbiz.core.account.helper.OAuthLoginState
import ai.govbiz.core.account.helper.OAuthStateHelper
import ai.govbiz.core.account.repository.AccountRepository
import ai.govbiz.core.account.service.dto.AccountSessionResult
import ai.govbiz.core.account.service.exception.AccountSuspendedException
import ai.govbiz.core.account.service.exception.OAuthLoginFailedException
import ai.govbiz.core.account.service.exception.OAuthLoginFailedException.Code
import java.net.URI
import java.net.URLEncoder
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service

/** 동의 화면으로 보낼 URL과, 콜백까지 브라우저가 들고 있을 서명된 상태입니다. */
data class OAuthLoginStart(
    val authorizationUrl: URI,
    val state: OAuthLoginState,
)

/**
 * Google·카카오 로그인입니다. 제공처 회원번호로 계정을 찾고, 없으면 제공처가 인증한 이메일로 기존 계정에 연결하거나
 * 비밀번호 없는 새 계정을 만듭니다. 세션 발급은 이메일 로그인과 같은 [AccountSessionService]를 씁니다.
 *
 * 이메일로 기존 계정에 붙이는 일은 제공처가 그 이메일을 인증했다고 답했을 때만 합니다. 인증되지 않은 이메일로
 * 자동 연결하면 남의 이메일을 적은 제공처 계정으로 그 사람의 계정에 들어갈 수 있기 때문입니다.
 */
@Service
class AccountOAuthService(
    private val repository: AccountRepository,
    private val sessionService: AccountSessionService,
    private val clients: OAuthProviderClients,
    private val properties: OAuthClientProperties,
    private val attemptGuard: AccountLoginAttemptGuard,
    @param:Qualifier("seoulClock") private val clock: Clock,
) {

    /** 버튼을 보여 줄 제공처입니다. 클라이언트 ID가 설정된 곳만 포함합니다. */
    fun enabledProviders(): List<OAuthProvider> = clients.enabledProviders

    /** 동의 화면 URL을 만듭니다. [next]는 로그인 뒤 돌아갈 앱 안 경로이며 외부 주소는 받지 않습니다. */
    fun start(provider: OAuthProvider, next: String?): OAuthLoginStart {
        val client = clients.client(provider)
        if (!client.isEnabled) {
            throw OAuthLoginFailedException(Code.PROVIDER_NOT_CONFIGURED, "${provider.key} login is not configured")
        }
        val codeVerifier = if (provider == OAuthProvider.GOOGLE) OAuthStateHelper.randomCodeVerifier() else null
        val state = OAuthLoginState(
            provider = provider,
            state = OAuthStateHelper.randomState(),
            codeVerifier = codeVerifier,
            next = sanitizeNext(next),
            expiresAt = Instant.now(clock).plus(STATE_TTL),
        )
        val url = client.authorizationUrl(
            state = state.state,
            redirectUri = redirectUri(provider),
            codeChallenge = codeVerifier?.let(OAuthStateHelper::codeChallenge),
        )
        return OAuthLoginStart(url, state)
    }

    /**
     * 콜백을 처리합니다. 저장된 상태와 제공처가 돌려준 `state`가 같아야 하고, 제공처가 오류를 돌려줬으면
     * 취소로 봅니다. 성공하면 "로그인 상태 유지"와 같은 긴 세션을 발급합니다.
     */
    fun complete(
        provider: OAuthProvider,
        stored: OAuthLoginState?,
        returnedState: String?,
        code: String?,
        providerError: String?,
        clientAddress: String,
    ): AccountSessionResult {
        if (stored == null || stored.provider != provider || returnedState.isNullOrBlank() || stored.state != returnedState) {
            throw OAuthLoginFailedException(Code.STATE_MISMATCH, "OAuth state did not match")
        }
        if (!providerError.isNullOrBlank() || code.isNullOrBlank()) {
            throw OAuthLoginFailedException(Code.PROVIDER_DENIED, "${provider.key} login was denied or cancelled")
        }
        attemptGuard.checkAddressAllowed(clientAddress)

        val identity = try {
            clients.client(provider).fetchIdentity(OAuthCodeExchange(code, redirectUri(provider), stored.codeVerifier))
        } catch (exception: OAuthClientException) {
            val failureCode = if (exception.failure == OAuthClientException.Failure.NOT_CONFIGURED) Code.PROVIDER_NOT_CONFIGURED else Code.PROVIDER_UNAVAILABLE
            throw OAuthLoginFailedException(failureCode, exception.message ?: "${provider.key} login failed", exception)
        }

        val account = signIn(identity)
        if (account.isSuspended) throw AccountSuspendedException()
        val issued = sessionService.issue(account.id, rememberMe = true)
        repository.createSession(account.id, issued.session)
        return sessionService.toResult(issued, account)
    }

    /** 제공처 사용자에 해당하는 계정을 찾거나 만듭니다. 이 안에서는 세션을 다루지 않습니다. */
    fun signIn(identity: OAuthIdentity): Account {
        repository.findSocialIdentity(identity.provider, identity.providerUserId)?.let { linked ->
            return requireNotNull(repository.findById(linked.accountId)) { "linked account was not readable" }
        }
        val email = identity.email
            ?: throw OAuthLoginFailedException(Code.EMAIL_REQUIRED, "${identity.provider.key} did not provide an email")
        val now = LocalDateTime.now(clock)

        val existing = repository.findByEmail(email)
        val account = if (existing != null) {
            if (!identity.emailVerified) {
                throw OAuthLoginFailedException(Code.EMAIL_NOT_VERIFIED, "${identity.provider.key} email is not verified for an existing account")
            }
            existing
        } else {
            createAccount(identity, email, now) ?: return signIn(identity)
        }

        return try {
            repository.linkSocialIdentity(NewSocialIdentity(account.id, identity.provider, identity.providerUserId, email, now))
            if (identity.emailVerified && !account.isEmailVerified) repository.markEmailVerified(account.id, now)
            requireNotNull(repository.findById(account.id)) { "account was not readable after linking" }
        } catch (_: DuplicateKeyException) {
            // 같은 순간 다른 요청이 먼저 연결했으면 그 결과를 따릅니다.
            signIn(identity)
        }
    }

    /** 비밀번호 없는 계정입니다. 같은 순간 같은 이메일이 먼저 만들어졌으면 null을 돌려줘 다시 조회하게 합니다. */
    private fun createAccount(identity: OAuthIdentity, email: String, now: LocalDateTime): Account? =
        try {
            repository.createAccount(
                NewAccount(
                    email = email,
                    passwordHash = null,
                    termsAgreedAt = now,
                    emailVerifiedAt = if (identity.emailVerified) now else null,
                ),
            )
        } catch (_: DuplicateKeyException) {
            null
        }

    /** 콜백 URL은 제공처 콘솔에 등록한 값과 글자 단위로 같아야 하므로 설정한 origin에 고정 경로를 붙입니다. */
    fun redirectUri(provider: OAuthProvider): URI =
        URI.create("${properties.redirectBaseUrl}$CALLBACK_PATH_PREFIX/${provider.key}/callback")

    /** 결과를 알려 줄 프런트 콜백 화면입니다. 오류면 `error`, 성공이면 돌아갈 `next`를 붙입니다. */
    fun frontendCallbackUri(next: String?, error: Code?): URI {
        val base = "${properties.frontendBaseUrl}$FRONTEND_CALLBACK_PATH"
        val query = if (error != null) "error=${error.name}" else "next=${URLEncoder.encode(sanitizeNext(next), Charsets.UTF_8)}"
        return URI.create("$base?$query")
    }

    private fun sanitizeNext(next: String?): String {
        val candidate = next?.trim().orEmpty()
        return if (candidate.startsWith("/") && !candidate.startsWith("//") && candidate.length <= MAX_NEXT_LENGTH) candidate else DEFAULT_NEXT
    }

    companion object {
        const val CALLBACK_PATH_PREFIX = "/api/v1/auth/oauth"
        const val FRONTEND_CALLBACK_PATH = "/oauth/callback"
        const val DEFAULT_NEXT = "/app/chat"
        const val MAX_NEXT_LENGTH = 512
        val STATE_TTL: Duration = Duration.ofMinutes(10)
    }
}
