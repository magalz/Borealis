package io.github.magalz.borealis.library

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import org.junit.Test

class SubnetCheckTest {
    private fun local(address: String, prefix: Int) =
        LocalIpv4(InetAddress.getByName(address) as Inet4Address, prefix)

    private fun check(vararg networks: LocalIpv4): (String) -> Boolean {
        val check = SubnetCheck { networks.toList() }
        return check::isWrongSiteLocalAddress
    }

    @Test
    fun `a private address on a subnet of this device is not wrong`() {
        val wrong = check(local("192.168.1.20", 24))

        assertThat(wrong("192.168.1.5")).isFalse()
    }

    @Test
    fun `a private address on no subnet of this device is wrong`() {
        val wrong = check(local("192.168.1.20", 24))

        assertThat(wrong("10.0.0.7")).isTrue()
        assertThat(wrong("192.168.2.5")).isTrue()
    }

    @Test
    fun `the prefix length is compared bit by bit from the most significant one`() {
        val wrong = check(local("192.168.17.20", 20))

        assertThat(wrong("192.168.31.1")).isFalse()
        assertThat(wrong("192.168.32.1")).isTrue()
        assertThat(wrong("192.168.15.1")).isTrue()
    }

    @Test
    fun `any of the interfaces can match`() {
        val wrong = check(local("10.1.0.2", 16), local("192.168.1.20", 24))

        assertThat(wrong("192.168.1.99")).isFalse()
        assertThat(wrong("10.1.200.3")).isFalse()
        assertThat(wrong("172.16.0.1")).isTrue()
    }

    @Test
    fun `a public address is never reported as being on the wrong subnet`() {
        val wrong = check(local("192.168.1.20", 24))

        assertThat(wrong("8.8.8.8")).isFalse()
    }

    @Test
    fun `an IPv6 address is not checked`() {
        val wrong = check(local("192.168.1.20", 24))

        assertThat(wrong("::1")).isFalse()
    }

    @Test
    fun `a host that does not resolve is not reported as the wrong subnet`() {
        val wrong = check(local("192.168.1.20", 24))

        assertThat(wrong("not a host name!")).isFalse()
    }

    @Test
    fun `when the interfaces cannot be listed the answer is no`() {
        val check = SubnetCheck { throw IOException("no interfaces") }

        assertThat(check.isWrongSiteLocalAddress("10.0.0.7")).isFalse()
    }
}
