package ai.govbiz.core.account.client.bizno.config

import java.net.URI
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class BiznoClientPropertiesTest {

    @Test
    fun derivesTheOriginFromTheEndpointUrlAndTrimsTheApiKey() {
        val properties = BiznoClientProperties(
            URI.create("https://bizno.net:8443/api/fapi"),
            "  key  ",
            Duration.ofSeconds(1),
            Duration.ofSeconds(2),
        )

        assertEquals(URI.create("https://bizno.net:8443"), properties.baseUrl)
        assertEquals("key", properties.apiKey)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://bizno.net/api/other",
            "https://bizno.net/",
            "https://bizno.net/api/fapi?key=x",
        ],
    )
    fun rejectsEndpointUrlsThatAreNotTheLookupPath(endpointUrl: String) {
        assertThrows(IllegalArgumentException::class.java) {
            BiznoClientProperties(
                URI.create(endpointUrl),
                "key",
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
            )
        }
    }

    @Test
    fun rejectsNonPositiveTimeouts() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            BiznoClientProperties(
                URI.create("https://bizno.net/api/fapi"),
                "key",
                Duration.ZERO,
                Duration.ofSeconds(2),
            )
        }

        assertEquals("app.bizno.connect-timeout must be greater than zero", exception.message)
    }
}
