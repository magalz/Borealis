package io.github.magalz.borealis.discovery

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.mdns.MdnsComputer
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The computer that mDNS discovery reports, in the inherited code: what it keeps, and when two of
 * them are the same host. `ComputerManagerService` (Java) reads it.
 *
 * Characterization: these tests pass on the Java class and on its Kotlin conversion.
 */
class MdnsComputerCharacterizationTest {
    private val v4 = ip4("192.168.1.5")
    private val v6 = ip6("2001:db8::1")

    @Test
    fun `a computer keeps its name, addresses and port`() {
        val computer = MdnsComputer("Den PC", v4, v6, 47989)

        assertThat(computer.name).isEqualTo("Den PC")
        assertThat(computer.localAddress).isSameInstanceAs(v4)
        assertThat(computer.ipv6Address).isSameInstanceAs(v6)
        assertThat(computer.port).isEqualTo(47989)
    }

    @Test
    fun `a computer may have no local or no IPv6 address`() {
        val computer = MdnsComputer("Den PC", null, null, 47989)

        assertThat(computer.localAddress).isNull()
        assertThat(computer.ipv6Address).isNull()
    }

    @Test
    fun `computers with the same name, port and addresses are equal`() {
        val first = MdnsComputer("Den PC", v4, v6, 47989)
        val second = MdnsComputer("Den PC", ip4("192.168.1.5"), ip6("2001:db8::1"), 47989)

        assertThat(first).isEqualTo(second)
        assertThat(second).isEqualTo(first)
        assertThat(first.hashCode()).isEqualTo(second.hashCode())
    }

    @Test
    fun `computers with no addresses at all are equal`() {
        assertThat(MdnsComputer("Den PC", null, null, 47989))
            .isEqualTo(MdnsComputer("Den PC", null, null, 47989))
    }

    @Test
    fun `a different name, port or address makes computers differ`() {
        val computer = MdnsComputer("Den PC", v4, v6, 47989)

        assertThat(computer).isNotEqualTo(MdnsComputer("Other PC", v4, v6, 47989))
        assertThat(computer).isNotEqualTo(MdnsComputer("Den PC", v4, v6, 47990))
        assertThat(computer).isNotEqualTo(MdnsComputer("Den PC", ip4("192.168.1.6"), v6, 47989))
        assertThat(computer).isNotEqualTo(MdnsComputer("Den PC", v4, ip6("2001:db9::1"), 47989))
    }

    @Test
    fun `an address that one computer has and the other lacks makes them differ, both ways`() {
        val withBoth = MdnsComputer("Den PC", v4, v6, 47989)
        val withoutLocal = MdnsComputer("Den PC", null, v6, 47989)
        val withoutV6 = MdnsComputer("Den PC", v4, null, 47989)

        assertThat(withBoth).isNotEqualTo(withoutLocal)
        assertThat(withoutLocal).isNotEqualTo(withBoth)
        assertThat(withBoth).isNotEqualTo(withoutV6)
        assertThat(withoutV6).isNotEqualTo(withBoth)
    }

    @Test
    fun `the hash code is the hash code of the name alone`() {
        assertThat(MdnsComputer("Den PC", v4, v6, 47989).hashCode()).isEqualTo("Den PC".hashCode())
        assertThat(MdnsComputer("Den PC", null, null, 1).hashCode()).isEqualTo("Den PC".hashCode())
    }

    @Test
    fun `a computer is never equal to something that is not a computer or to nothing`() {
        val computer = MdnsComputer("Den PC", v4, v6, 47989)

        assertThat(computer.equals("Den PC")).isFalse()
        assertThat(computer).isNotEqualTo(null)
    }

    @Test
    fun `the hash code of a computer with no name throws a NullPointerException`() {
        assertThrows(NullPointerException::class.java) {
            MdnsComputer(null, v4, v6, 47989).hashCode()
        }
    }

    @Test
    fun `equals throws a NullPointerException when the computer it compares with has no name`() {
        val named = MdnsComputer("Den PC", v4, v6, 47989)
        val unnamed = MdnsComputer(null, v4, v6, 47989)

        assertThrows(NullPointerException::class.java) { named.equals(unnamed) }
        assertThrows(NullPointerException::class.java) { unnamed.equals(unnamed) }
    }

    @Test
    fun `a computer with no name differs from a named one when it is the one that compares`() {
        val named = MdnsComputer("Den PC", v4, v6, 47989)
        val unnamed = MdnsComputer(null, v4, v6, 47989)

        assertThat(unnamed.equals(named)).isFalse()
    }

    @Test
    fun `toString shows the name and the two addresses, and not the port`() {
        assertThat(MdnsComputer("Den PC", v4, v6, 47989).toString())
            .isEqualTo("[Den PC - $v4 - $v6]")
        assertThat(MdnsComputer("Den PC", null, null, 47989).toString())
            .isEqualTo("[Den PC - null - null]")
        assertThat(MdnsComputer(null, null, null, 47989).toString())
            .isEqualTo("[null - null - null]")
    }
}
