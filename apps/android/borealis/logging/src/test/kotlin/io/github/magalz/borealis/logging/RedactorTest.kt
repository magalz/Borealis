package io.github.magalz.borealis.logging

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RedactorTest {
    private val redactor = Redactor()

    @Test
    fun `labelled pairing pins are redacted`() {
        assertThat(redactor.redact("pairing pin=1234")).isEqualTo("pairing pin=[REDACTED]")
        assertThat(redactor.redact("PIN 1234 entered")).isEqualTo("PIN [REDACTED] entered")
        assertThat(redactor.redact("pin: 0042")).isEqualTo("pin: [REDACTED]")
        assertThat(redactor.redact("""{"pin":"1234"}""")).isEqualTo("""{"pin":"[REDACTED]"}""")
    }

    @Test
    fun `numbers that are not labelled pins are untouched`() {
        val text =
            "port 47984 open, build 1234, pin the window, pinned 1234 items, pin 123456789, spin 1234"
        assertThat(redactor.redact(text)).isEqualTo(text)
    }

    @Test
    fun `bearer tokens are redacted`() {
        assertThat(redactor.redact("Authorization: Bearer abc.def-123_xyz=="))
            .isEqualTo("Authorization: Bearer [REDACTED]")
        assertThat(redactor.redact("header bearer Zm9vYmFy done"))
            .isEqualTo("header bearer [REDACTED] done")
    }

    @Test
    fun `token and apiToken values are redacted`() {
        assertThat(redactor.redact("token=abc123 next")).isEqualTo("token=[REDACTED] next")
        assertThat(redactor.redact("apiToken=xyz")).isEqualTo("apiToken=[REDACTED]")
        assertThat(redactor.redact("""{"apiToken":"s3cr3t"}"""))
            .isEqualTo("""{"apiToken":"[REDACTED]"}""")
        assertThat(redactor.redact("access_token: abc")).isEqualTo("access_token: [REDACTED]")
    }

    @Test
    fun `words that merely contain token are untouched`() {
        val text = "tokenizer = fast, token count is high"
        assertThat(redactor.redact(text)).isEqualTo(text)
    }

    @Test
    fun `pem blocks are redacted`() {
        val text =
            "cert follows\n" +
                "-----BEGIN CERTIFICATE-----\nMIIBIjANBgkqhkiG9w0B\nAQEFAAOCAQ8A\n-----END CERTIFICATE-----\n" +
                "after"

        assertThat(redactor.redact(text)).isEqualTo("cert follows\n[REDACTED]\nafter")
    }

    @Test
    fun `a truncated pem block is redacted to the end`() {
        val text = "key -----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0B"

        assertThat(redactor.redact(text)).isEqualTo("key [REDACTED]")
    }

    @Test
    fun `long hex and base64 values in cert key and signature fields are redacted`() {
        val hex = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90"
        val b64 = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAu1SU1LfVLPHCozMxH2Mo"

        assertThat(redactor.redact("cert=$hex ok")).isEqualTo("cert=[REDACTED] ok")
        assertThat(redactor.redact("clientCert=$hex")).isEqualTo("clientCert=[REDACTED]")
        assertThat(redactor.redact("key=$hex")).isEqualTo("key=[REDACTED]")
        assertThat(redactor.redact("signature=$b64")).isEqualTo("signature=[REDACTED]")
        assertThat(redactor.redact("""{"key":"$b64"}""")).isEqualTo("""{"key":"[REDACTED]"}""")
    }

    @Test
    fun `short values in key like fields are untouched`() {
        val text = "key=value cert=ok signature=none monkey=banana"
        assertThat(redactor.redact(text)).isEqualTo(text)
    }

    @Test
    fun `ordinary text is untouched`() {
        val text = "Connected to host Gaming-PC in 42 ms (codec=hevc, bitrate=20000 kbps)"
        assertThat(redactor.redact(text)).isEqualTo(text)
    }

    @Test
    fun `ip addresses are kept by default`() {
        val text = "connect 192.168.1.10 and fe80::1ff:fe23:4567:890a"
        assertThat(redactor.redact(text)).isEqualTo(text)
    }

    @Test
    fun `ip addresses are masked when the switch is on`() {
        val masking = Redactor(maskAddresses = true)

        assertThat(masking.redact("connect 192.168.1.10:47989 ok"))
            .isEqualTo("connect [IP]:47989 ok")
        assertThat(masking.redact("v6 fe80::1ff:fe23:4567:890a and 2001:db8::1 and ::1 end"))
            .isEqualTo("v6 [IP] and [IP] and [IP] end")
        assertThat(masking.redact("full 2001:0db8:0000:0000:0000:ff00:0042:8329 end"))
            .isEqualTo("full [IP] end")
    }

    @Test
    fun `masking leaves times and versions alone`() {
        val masking = Redactor(maskAddresses = true)
        val text = "at 12:34:56 version 1.2.3 build 12:34"

        assertThat(masking.redact(text)).isEqualTo(text)
    }

    @Test
    fun `sensitive field names redact the whole value`() {
        assertThat(redactor.redactField("pin", "1234")).isEqualTo("[REDACTED]")
        assertThat(redactor.redactField("apiToken", "two words here")).isEqualTo("[REDACTED]")
        assertThat(redactor.redactField("password", "hunter2")).isEqualTo("[REDACTED]")
        assertThat(redactor.redactField("Authorization", "Basic abc")).isEqualTo("[REDACTED]")
    }

    @Test
    fun `other field values are redacted like text`() {
        assertThat(redactor.redactField("host", "gaming-pc")).isEqualTo("gaming-pc")
        assertThat(redactor.redactField("note", "retry with pin=1234"))
            .isEqualTo("retry with pin=[REDACTED]")
        assertThat(redactor.redactField("cert", "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4"))
            .isEqualTo("[REDACTED]")
    }
}
