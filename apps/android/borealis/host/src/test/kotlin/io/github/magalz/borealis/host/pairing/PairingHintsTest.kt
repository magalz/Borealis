package io.github.magalz.borealis.host.pairing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PairingHintsTest {
    @Test
    fun `the Web UI is the HTTPS server on the HTTP port plus one`() {
        assertThat(PairingHints.webUiUrl("192.0.2.10", 47989)).isEqualTo("https://192.0.2.10:47990")
        assertThat(PairingHints.webUiUrl("my-pc.local", 1)).isEqualTo("https://my-pc.local:2")
        assertThat(PairingHints.webUiUrl("192.0.2.10", 65534)).isEqualTo("https://192.0.2.10:65535")
    }

    @Test
    fun `IPv6 literals are bracketed once`() {
        assertThat(PairingHints.webUiUrl("2001:db8::10", 47989))
            .isEqualTo("https://[2001:db8::10]:47990")
        assertThat(PairingHints.webUiUrl("[2001:db8::10]", 47989))
            .isEqualTo("https://[2001:db8::10]:47990")
    }

    @Test
    fun `a zone id is percent-encoded as RFC 6874 requires`() {
        assertThat(PairingHints.webUiUrl("fe80::1%wlan0", 47989))
            .isEqualTo("https://[fe80::1%25wlan0]:47990")
        assertThat(PairingHints.webUiUrl("[fe80::1%wlan 0]", 47989))
            .isEqualTo("https://[fe80::1%25wlan%200]:47990")
    }

    @Test
    fun `an unusable port is rejected`() {
        for (port in listOf(Int.MIN_VALUE, -1, 0, 65535, 65536, Int.MAX_VALUE)) {
            assertThat(PairingHints.webUiUrl("192.0.2.10", port)).isNull()
        }
    }

    @Test
    fun `an unusable address is rejected`() {
        for (address in listOf("", "  ", "a b", "host/path", "host?x", "host#x", "u@host", "[]")) {
            assertThat(PairingHints.webUiUrl(address, 47989)).isNull()
        }
    }

    @Test
    fun `the suggested name is the device model`() {
        assertThat(PairingHints.suggestedDeviceName("Pixel 8")).isEqualTo("Pixel 8")
        assertThat(PairingHints.suggestedDeviceName("  Pixel   8 ")).isEqualTo("Pixel 8")
    }

    @Test
    fun `xbox and webos are removed whatever their case`() {
        assertThat(PairingHints.suggestedDeviceName("Xbox Phone")).isEqualTo("Phone")
        assertThat(PairingHints.suggestedDeviceName("my-WebOS-TV")).isEqualTo("my--TV")
        assertThat(PairingHints.suggestedDeviceName("XBOXwebosXBOX")).isEqualTo("Borealis")
    }

    @Test
    fun `removal that would join a forbidden word is repeated`() {
        val name = PairingHints.suggestedDeviceName("xbwebosox Tab")

        assertThat(name.lowercase()).doesNotContain("xbox")
        assertThat(name).isEqualTo("Tab")
    }

    @Test
    fun `a missing or empty model falls back to Borealis`() {
        assertThat(PairingHints.suggestedDeviceName(null)).isEqualTo("Borealis")
        assertThat(PairingHints.suggestedDeviceName("")).isEqualTo("Borealis")
        assertThat(PairingHints.suggestedDeviceName("   ")).isEqualTo("Borealis")
        assertThat(PairingHints.suggestedDeviceName("xbox")).isEqualTo("Borealis")
    }
}
