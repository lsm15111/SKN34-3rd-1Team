package ai.govbiz.core.account.client.oauth.config

import java.net.http.HttpClient
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder
import org.springframework.boot.http.client.HttpClientSettings
import org.springframework.boot.http.client.HttpRedirects
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient

/** 제공처마다 host가 달라(kauth·kapi, oauth2·openidconnect) baseUrl 없이 절대 URI로 호출하는 RestClient입니다. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OAuthClientProperties::class)
class OAuthClientConfig {

    @Bean
    fun oauthRestClient(
        restClientBuilder: RestClient.Builder,
        properties: OAuthClientProperties,
    ): RestClient {
        val settings = HttpClientSettings.defaults()
            .withConnectTimeout(properties.connectTimeout)
            .withReadTimeout(properties.readTimeout)
            .withRedirects(HttpRedirects.DONT_FOLLOW)
        val requestFactory = ClientHttpRequestFactoryBuilder.jdk()
            .withHttpClientCustomizer { httpClientBuilder -> httpClientBuilder.version(HttpClient.Version.HTTP_1_1) }
            .build(settings)
        return restClientBuilder
            .requestFactory(requestFactory)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build()
    }
}
