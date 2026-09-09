package ai.govbiz.core.account.client.oauth

import ai.govbiz.core.account.domain.OAuthProvider
import org.springframework.stereotype.Component

/** 제공처별 Client를 한곳에서 찾습니다. 설정이 비어 켜지지 않은 제공처는 [enabledProviders]에 없습니다. */
@Component
class OAuthProviderClients(clients: List<OAuthProviderClient>) {

    private val byProvider: Map<OAuthProvider, OAuthProviderClient> = clients.associateBy(OAuthProviderClient::provider)

    init {
        require(byProvider.size == OAuthProvider.entries.size) { "every OAuthProvider needs exactly one client" }
    }

    val enabledProviders: List<OAuthProvider>
        get() = OAuthProvider.entries.filter { provider -> requireNotNull(byProvider[provider]).isEnabled }

    fun client(provider: OAuthProvider): OAuthProviderClient = requireNotNull(byProvider[provider])
}
