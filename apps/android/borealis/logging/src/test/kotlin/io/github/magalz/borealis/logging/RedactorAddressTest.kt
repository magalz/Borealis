package io.github.magalz.borealis.logging

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Address masking: IPv4 with octets of at most 255, IPv6 and host names. */
class RedactorAddressTest {
    private val masking = Redactor(maskAddresses = true)

    @Test
    fun `valid ipv4 addresses are masked`() {
        assertThat(masking.redact("a 192.0.2.10 b 10.0.0.1:47989 c 255.255.255.255 d 0.0.0.0"))
            .isEqualTo("a [IP] b [IP]:47989 c [IP] d [IP]")
    }

    @Test
    fun `dotted numbers that are not addresses stay intact`() {
        val text = "ver 7.1.431.0 and 7.1.431.-1 and 256.1.1.1 and 1.2.3.4.5 and 300.1.1.1 end"

        assertThat(masking.redact(text)).isEqualTo(text)
    }

    @Test
    fun `an address at the end of a sentence is masked`() {
        assertThat(masking.redact("connected to 192.0.2.10.")).isEqualTo("connected to [IP].")
    }

    @Test
    fun `ipv6 addresses are masked as before`() {
        assertThat(masking.redact("v6 fe80::1ff:fe23:4567:890a and 2001:db8::1 end"))
            .isEqualTo("v6 [IP] and [IP] end")
    }

    @Test
    fun `the host of a url is masked`() {
        assertThat(masking.redact("https://gaming-pc.local:47984/serverinfo?mode=1"))
            .isEqualTo("https://[HOST]:47984/serverinfo?mode=1")
        assertThat(masking.redact("http://192.0.2.5:47989/x")).isEqualTo("http://[HOST]:47989/x")
        assertThat(masking.redact("http://[2001:db8::1]:47989/x"))
            .isEqualTo("http://[HOST]:47989/x")
    }

    @Test
    fun `values of host and computer keys are masked in text`() {
        assertThat(masking.redact("paired host=Gaming-PC ok")).isEqualTo("paired host=[HOST] ok")
        assertThat(masking.redact("computerName=Den-PC")).isEqualTo("computerName=[HOST]")
        assertThat(masking.redact("""{"hostname":"den-pc"}"""))
            .isEqualTo("""{"hostname":"[HOST]"}""")
    }

    @Test
    fun `values of host and computer fields are masked whole`() {
        val keys = listOf("host", "hostname", "serverHost", "computer", "computerName")
        for (key in keys) {
            assertThat(masking.redactField(key, "My Gaming PC")).isEqualTo("[HOST]")
        }
    }

    @Test
    fun `other fields and words containing host are not masked`() {
        assertThat(masking.redactField("ghostly", "boo")).isEqualTo("boo")
        assertThat(masking.redactField("status", "ok")).isEqualTo("ok")
        val prose = "the host is busy, hosts differ"
        assertThat(masking.redact(prose)).isEqualTo(prose)
    }

    @Test
    fun `nothing is masked while the switch is off`() {
        val off = Redactor()
        val text = "host=Gaming-PC https://gaming-pc:47984/x?mode=1 10.0.0.5 2001:db8::1"

        assertThat(off.redact(text)).isEqualTo(text)
        assertThat(off.redactField("computerName", "Den PC")).isEqualTo("Den PC")
    }

    @Test
    fun `the switch can be flipped while the redactor is in use`() {
        val redactor = Redactor()
        assertThat(redactor.redact("to 10.0.0.5")).isEqualTo("to 10.0.0.5")

        redactor.maskAddresses = true
        assertThat(redactor.redact("to 10.0.0.5")).isEqualTo("to [IP]")

        redactor.maskAddresses = false
        assertThat(redactor.redact("to 10.0.0.5")).isEqualTo("to 10.0.0.5")
    }

    @Test
    fun `quoted host values are masked up to the closing quote`() {
        assertThat(masking.redact("""computerName="Den PC" ok"""))
            .isEqualTo("""computerName="[HOST]" ok""")
        assertThat(masking.redact("host='Gaming PC', next")).isEqualTo("host='[HOST]', next")
        assertThat(masking.redact("""{"computer":"Den PC","port":1}"""))
            .isEqualTo("""{"computer":"[HOST]","port":1}""")
    }

    @Test
    fun `an unterminated quoted host value is masked to the end of the line`() {
        assertThat(masking.redact("""host="Den PC""" + "\nnext line"))
            .isEqualTo("""host="[HOST]""" + "\nnext line")
    }
}
