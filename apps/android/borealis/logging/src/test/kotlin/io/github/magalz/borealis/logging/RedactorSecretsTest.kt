package io.github.magalz.borealis.logging

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Pairing/launch secrets that upstream NvHTTP and PairingManager log, plus generic secrets. */
class RedactorSecretsTest {
    private val redactor = Redactor()

    private val canaries = RedactionCorpus.canaries()

    // --- the shared canary corpus (core/tests/fixtures/redaction) ---

    @Test
    fun `the corpus is not empty`() {
        assertThat(canaries.size).isAtLeast(30)
    }

    @Test
    fun `every canary is redacted to its expected text`() {
        for (canary in canaries) {
            assertThat(redactor.redact(canary.input)).isEqualTo(canary.expected)
        }
    }

    @Test
    fun `no canary secret survives with address masking off or on`() {
        for (masking in listOf(false, true)) {
            val redactor = Redactor(maskAddresses = masking)
            for (canary in canaries) {
                assertThat(redactor.redact(canary.input)).doesNotContain(canary.secret)
                assertThat(redactor.redactField("note", canary.input)).doesNotContain(canary.secret)
            }
        }
    }

    @Test
    fun `redacted canaries stay unchanged when redacted again`() {
        for (canary in canaries) {
            assertThat(redactor.redact(canary.expected)).isEqualTo(canary.expected)
        }
    }

    // --- M1: pairing and launch secrets ---

    @Test
    fun `uniqueid stays visible`() {
        val text = "uniqueid=0123456789ABCDEF"
        assertThat(redactor.redact(text)).isEqualTo(text)
    }

    @Test
    fun `pairing field names redact the whole value`() {
        for (key in
            listOf(
                "salt",
                "clientchallenge",
                "serverchallengeresp",
                "clientpairingsecret",
                "pairingsecret",
                "challengeresponse",
                "plaincert",
                "rikey",
                "ri_key",
            )) {
            assertThat(redactor.redactField(key, "any value")).isEqualTo("[REDACTED]")
        }
        assertThat(redactor.redactField("rikeyid", "-1234")).isEqualTo("-1234")
        assertThat(redactor.redactField("uniqueid", "0123456789ABCDEF"))
            .isEqualTo("0123456789ABCDEF")
    }

    // --- M2: pin variants ---

    @Test
    fun `pin field names redact the whole value`() {
        for (key in listOf("pairingPin", "pin_code", "userPin", "pin")) {
            assertThat(redactor.redactField(key, "1234")).isEqualTo("[REDACTED]")
        }
    }

    @Test
    fun `words near pin stay untouched`() {
        val text = "pinned 1234, spin 1234, mapping 1234, pin the window, pinball 5678"
        assertThat(redactor.redact(text)).isEqualTo(text)
    }

    // --- M3: generic secrets with values of any length ---

    @Test
    fun `cookies are redacted to the end of the line`() {
        assertThat(redactor.redact("Cookie: session=abc; other=1\nnext line"))
            .isEqualTo("Cookie: [REDACTED]\nnext line")
        assertThat(redactor.redact("Set-Cookie: id=5; Path=/")).isEqualTo("Set-Cookie: [REDACTED]")
    }

    @Test
    fun `secrets without a value separator are untouched`() {
        val text = "password is required, secret santa, passwordless login"
        assertThat(redactor.redact(text)).isEqualTo(text)
    }

    // --- performance and size cap ---

    @Test
    fun `a huge hyphenated string is processed quickly`() {
        val hostile = "a-".repeat(50_000)

        val start = System.nanoTime()
        val result = redactor.redact(hostile)
        val millis = (System.nanoTime() - start) / 1_000_000

        assertThat(millis).isLessThan(1_000)
        assertThat(result).contains("[truncated")
    }

    @Test
    fun `long text is capped and marked as truncated`() {
        val result = redactor.redact("x".repeat(20_000))

        assertThat(result.length).isLessThan(20_000)
        assertThat(result).startsWith("x".repeat(Redactor.DEFAULT_MAX_LENGTH))
        assertThat(result).endsWith("...[truncated 3616 chars]")
    }

    @Test
    fun `text within the cap is not marked`() {
        assertThat(redactor.redact("x".repeat(Redactor.DEFAULT_MAX_LENGTH)))
            .doesNotContain("truncated")
    }

    @Test
    fun `callers may pass a larger cap`() {
        val text = "x".repeat(40_000)

        assertThat(redactor.redact(text, maxLength = 50_000)).isEqualTo(text)
    }

    @Test
    fun `a secret cut by the cap is still redacted`() {
        val text = "y".repeat(Redactor.DEFAULT_MAX_LENGTH - 8) + " token=abcdefghijklmnop"

        assertThat(redactor.redact(text)).doesNotContain("abcd")
    }
}
