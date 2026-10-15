package io.github.magalz.borealis.library.domain

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.library.domain.HostAddressParser.ParseResult
import org.junit.Test

class HostAddressParserTest {
    private fun valid(host: String, port: Int) = ParseResult.Valid(HostAddress(host, port))

    @Test
    fun `an IPv4 address without a port gets the default port`() {
        assertThat(HostAddressParser.parse("192.168.1.20")).isEqualTo(valid("192.168.1.20", 47989))
    }

    @Test
    fun `an IPv4 address with a port keeps it`() {
        assertThat(HostAddressParser.parse("192.168.1.20:48000"))
            .isEqualTo(valid("192.168.1.20", 48000))
    }

    @Test
    fun `a host name is accepted with and without a port`() {
        assertThat(HostAddressParser.parse("desk-pc.local"))
            .isEqualTo(valid("desk-pc.local", 47989))
        assertThat(HostAddressParser.parse("desk-pc.local:47990"))
            .isEqualTo(valid("desk-pc.local", 47990))
    }

    @Test
    fun `a bracketed IPv6 address keeps the brackets out of the host`() {
        assertThat(HostAddressParser.parse("[::1]:47990")).isEqualTo(valid("::1", 47990))
        assertThat(HostAddressParser.parse("[fe80::1]")).isEqualTo(valid("fe80::1", 47989))
    }

    @Test
    fun `a bare IPv6 address is accepted as the 12 point 2 field accepts it`() {
        assertThat(HostAddressParser.parse("::1")).isEqualTo(valid("::1", 47989))
    }

    @Test
    fun `the input is trimmed`() {
        assertThat(HostAddressParser.parse("  192.168.1.20 \n"))
            .isEqualTo(valid("192.168.1.20", 47989))
    }

    @Test
    fun `blank input is empty`() {
        assertThat(HostAddressParser.parse("")).isEqualTo(ParseResult.Empty)
        assertThat(HostAddressParser.parse("   ")).isEqualTo(ParseResult.Empty)
    }

    @Test
    fun `a port outside 1 to 65535 is invalid`() {
        for (input in listOf("pc:0", "pc:65536", "pc:-1", "[::1]:70000", "pc:99999999999")) {
            assertThat(HostAddressParser.parse(input)).isEqualTo(ParseResult.Invalid)
        }
    }

    @Test
    fun `the boundary ports are valid`() {
        assertThat(HostAddressParser.parse("pc:1")).isEqualTo(valid("pc", 1))
        assertThat(HostAddressParser.parse("pc:65535")).isEqualTo(valid("pc", 65535))
    }

    @Test
    fun `malformed input is invalid`() {
        val malformed =
            listOf(
                "pc:",
                "pc:abc",
                ":47989",
                "[]",
                "[::1",
                "[::1]x",
                "[::1]:",
                "[pc]",
                "http://pc",
                "pc/path",
                "user@pc",
                "my pc",
                "a:b:zz",
            )
        for (input in malformed) {
            assertThat(HostAddressParser.parse(input)).isEqualTo(ParseResult.Invalid)
        }
    }
}
