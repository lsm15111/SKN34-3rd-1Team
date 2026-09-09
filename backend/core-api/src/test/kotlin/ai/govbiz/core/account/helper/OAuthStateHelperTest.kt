package ai.govbiz.core.account.helper

import ai.govbiz.core.account.domain.OAuthProvider
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OAuthStateHelperTest {

    private val now: Instant = Instant.parse("2026-09-06T03:00:00Z")
    private val state = OAuthLoginState(
        provider = OAuthProvider.GOOGLE,
        state = OAuthStateHelper.randomState(),
        codeVerifier = OAuthStateHelper.randomCodeVerifier(),
        next = "/app/partners",
        expiresAt = now.plusSeconds(600),
    )

    @Test
    fun roundTripsASignedStateAndRejectsTamperingOtherSecretsAndExpiry() {
        val value = OAuthStateHelper.issue(state, AccountTestHelper.JWT_SECRET)

        assertEquals(state, OAuthStateHelper.verify(value, AccountTestHelper.JWT_SECRET, now))
        assertNull(OAuthStateHelper.verify(value, "another-secret-0123456789abcdef0123456789", now))
        assertNull(OAuthStateHelper.verify(value, AccountTestHelper.JWT_SECRET, now.plusSeconds(600)))
        assertNull(OAuthStateHelper.verify(null, AccountTestHelper.JWT_SECRET, now))
        assertNull(OAuthStateHelper.verify("not.a.state", AccountTestHelper.JWT_SECRET, now))

        val (payload, signature) = value.split('.')
        val tampered = payload.dropLast(2) + "AA" + "." + signature
        assertNull(OAuthStateHelper.verify(tampered, AccountTestHelper.JWT_SECRET, now))
    }

    @Test
    fun generatesUnpredictableStatesAndAnRfc7636CodeChallenge() {
        assertNotEquals(OAuthStateHelper.randomState(), OAuthStateHelper.randomState())
        val verifier = OAuthStateHelper.randomCodeVerifier()
        assertTrue(verifier.length in 43..128)
        assertTrue(verifier.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        // RFC 7636 부록 B의 예시 값입니다.
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            OAuthStateHelper.codeChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }
}
