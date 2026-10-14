package io.github.magalz.borealis.logging

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Pairing/launch secrets that upstream NvHTTP and PairingManager log, plus generic secrets. */
class RedactorSecretsTest {
    private val redactor = Redactor()

    private val hex32 = "0123456789ABCDEF0123456789ABCDEF"
    private val hex64 = hex32 + hex32
    private val cert = "3082".repeat(60)

    // --- M1: pairing and launch secrets ---

    @Test
    fun `pairing request parameters are redacted`() {
        assertThat(redactor.redact("salt=$hex32")).isEqualTo("salt=[REDACTED]")
        assertThat(redactor.redact("clientchallenge=$hex32"))
            .isEqualTo("clientchallenge=[REDACTED]")
        assertThat(redactor.redact("serverchallengeresp=$hex64"))
            .isEqualTo("serverchallengeresp=[REDACTED]")
        assertThat(redactor.redact("clientpairingsecret=$hex64"))
            .isEqualTo("clientpairingsecret=[REDACTED]")
    }

    @Test
    fun `pairing response xml elements are redacted`() {
        assertThat(redactor.redact("<pairingsecret>$hex64</pairingsecret>"))
            .isEqualTo("<pairingsecret>[REDACTED]</pairingsecret>")
        assertThat(redactor.redact("<challengeresponse>$hex64</challengeresponse>"))
            .isEqualTo("<challengeresponse>[REDACTED]</challengeresponse>")
        assertThat(redactor.redact("<plaincert>$cert</plaincert>"))
            .isEqualTo("<plaincert>[REDACTED]</plaincert>")
        assertThat(redactor.redact("<serverchallengeresp>$hex64</serverchallengeresp>"))
            .isEqualTo("<serverchallengeresp>[REDACTED]</serverchallengeresp>")
    }

    @Test
    fun `a full pairing url keeps only harmless parameters`() {
        val url =
            "https://10.0.0.5:47984/pair?uniqueid=0123456789ABCDEF&uuid=abc&devicename=roth" +
                "&updateState=1&phrase=getservercert&salt=$hex32&clientcert=$cert"

        assertThat(redactor.redact(url))
            .isEqualTo(
                "https://10.0.0.5:47984/pair?uniqueid=0123456789ABCDEF&uuid=abc&devicename=roth" +
                    "&updateState=1&phrase=getservercert&salt=[REDACTED]&clientcert=[REDACTED]"
            )
    }

    @Test
    fun `the launch url hides rikey but keeps rikeyid`() {
        val url =
            "https://h:47984/launch?appid=1&mode=1920x1080x60&rikey=$hex32&rikeyid=-1234&localAudioPlayMode=0"

        assertThat(redactor.redact(url))
            .isEqualTo(
                "https://h:47984/launch?appid=1&mode=1920x1080x60&rikey=[REDACTED]&rikeyid=-1234&localAudioPlayMode=0"
            )
    }

    @Test
    fun `rikey is redacted in quoted colon and url encoded forms`() {
        assertThat(redactor.redact("""rikey="$hex32"""")).isEqualTo("""rikey="[REDACTED]"""")
        assertThat(redactor.redact("ri_key: $hex32")).isEqualTo("ri_key: [REDACTED]")
        assertThat(redactor.redact("a%26RIKEY%3D$hex32%26rikeyid%3D5"))
            .isEqualTo("a%26RIKEY%3D[REDACTED]%26rikeyid%3D5")
    }

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
    fun `pin variants are redacted`() {
        assertThat(redactor.redact("pairing_pin=1234")).isEqualTo("pairing_pin=[REDACTED]")
        assertThat(redactor.redact("pin_code=1234")).isEqualTo("pin_code=[REDACTED]")
        assertThat(redactor.redact("userPin: 1234")).isEqualTo("userPin: [REDACTED]")
        assertThat(redactor.redact("pin=12345")).isEqualTo("pin=[REDACTED]")
        assertThat(redactor.redact("pin=12345678")).isEqualTo("pin=[REDACTED]")
        assertThat(redactor.redact("PIN is 1234")).isEqualTo("PIN is [REDACTED]")
        assertThat(redactor.redact("pairingPin = 4321")).isEqualTo("pairingPin = [REDACTED]")
    }

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
    fun `passwords and secrets of any length are redacted`() {
        assertThat(redactor.redact("password=hunter2")).isEqualTo("password=[REDACTED]")
        assertThat(redactor.redact("storePassword=ab")).isEqualTo("storePassword=[REDACTED]")
        assertThat(redactor.redact("secret=abcd")).isEqualTo("secret=[REDACTED]")
        assertThat(redactor.redact("client_secret: s3")).isEqualTo("client_secret: [REDACTED]")
    }

    @Test
    fun `api keys are redacted`() {
        val key = "abcdefghijklmnopqrstuvwxyz012345"
        assertThat(redactor.redact("api_key=$key")).isEqualTo("api_key=[REDACTED]")
        assertThat(redactor.redact("x-api-key: $key")).isEqualTo("x-api-key: [REDACTED]")
        assertThat(redactor.redact("apiKey=short")).isEqualTo("apiKey=[REDACTED]")
    }

    @Test
    fun `authorization headers keep only the scheme`() {
        assertThat(redactor.redact("Authorization: Basic dXNlcjpwYXNz"))
            .isEqualTo("Authorization: Basic [REDACTED]")
        assertThat(redactor.redact("Authorization: Bearer abc.def"))
            .isEqualTo("Authorization: Bearer [REDACTED]")
        assertThat(redactor.redact("authorization=xyz")).isEqualTo("authorization=[REDACTED]")
    }

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
