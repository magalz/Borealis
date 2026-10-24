package io.github.magalz.borealis.hostmodel

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import java.util.Objects
import org.junit.Assert.assertThrows
import org.junit.Test

/** Characterizes the address tuple of the host model as it was before the Kotlin conversion. */
class AddressTupleTest {
    @Test
    fun `the constructor keeps the address and the port`() {
        val tuple = AddressTuple("192.168.1.5", 47989)

        assertThat(tuple.address).isEqualTo("192.168.1.5")
        assertThat(tuple.port).isEqualTo(47989)
    }

    @Test
    fun `the brackets of an escaped IPv6 address are removed`() {
        assertThat(AddressTuple("[fe80::1]", 47989).address).isEqualTo("fe80::1")
        assertThat(AddressTuple("[a]", 1).address).isEqualTo("a")
        assertThat(AddressTuple("[]", 1).address).isEmpty()
    }

    @Test
    fun `only a pair of brackets is removed`() {
        assertThat(AddressTuple("[fe80::1", 1).address).isEqualTo("[fe80::1")
        assertThat(AddressTuple("fe80::1]", 1).address).isEqualTo("fe80::1]")
        assertThat(AddressTuple("[[fe80::1]]", 1).address).isEqualTo("[fe80::1]")
        assertThat(AddressTuple("[", 1).address).isEqualTo("[")
    }

    @Test
    fun `a null address is rejected`() {
        val error =
            assertThrows(IllegalArgumentException::class.java) { AddressTuple(null, 47989) }

        assertThat(error).hasMessageThat().isEqualTo("Address cannot be null")
    }

    @Test
    fun `a port that is not positive is rejected`() {
        for (port in listOf(0, -1, Int.MIN_VALUE)) {
            val error =
                assertThrows(IllegalArgumentException::class.java) {
                    AddressTuple("pc.local", port)
                }

            assertThat(error).hasMessageThat().isEqualTo("Invalid port")
        }
    }

    @Test
    fun `a null address is reported before a bad port`() {
        val error = assertThrows(IllegalArgumentException::class.java) { AddressTuple(null, 0) }

        assertThat(error).hasMessageThat().isEqualTo("Address cannot be null")
    }

    @Test
    fun `an empty address and a port above 65535 are accepted`() {
        val tuple = AddressTuple("", 70000)

        assertThat(tuple.address).isEmpty()
        assertThat(tuple.port).isEqualTo(70000)
    }

    @Test
    fun `tuples are equal when the address and the port are equal`() {
        val tuple = AddressTuple("pc.local", 47989)

        assertThat(tuple).isEqualTo(tuple)
        assertThat(tuple).isEqualTo(AddressTuple("pc.local", 47989))
        assertThat(tuple).isNotEqualTo(AddressTuple("pc.local", 47990))
        assertThat(tuple).isNotEqualTo(AddressTuple("other.local", 47989))
        assertThat(tuple).isNotEqualTo(AddressTuple("PC.local", 47989))
    }

    @Test
    fun `a tuple is not equal to null or to another type`() {
        val tuple = AddressTuple("pc.local", 47989)
        val missing: Any? = null
        val text: Any = "pc.local:47989"

        assertThat(tuple == missing).isFalse()
        assertThat(tuple == text).isFalse()
    }

    @Test
    fun `an escaped and a bare IPv6 address make equal tuples`() {
        assertThat(AddressTuple("[fe80::1]", 47989)).isEqualTo(AddressTuple("fe80::1", 47989))
    }

    @Test
    fun `a tuple follows the fields that the service mutates`() {
        val tuple = AddressTuple("pc.local", 47989)
        tuple.port = 50000
        tuple.address = "other.local"

        assertThat(tuple).isEqualTo(AddressTuple("other.local", 50000))
        assertThat(tuple).isNotEqualTo(AddressTuple("pc.local", 47989))
        assertThat(tuple.toString()).isEqualTo("other.local:50000")
    }

    @Test
    fun `the hash code is the one of the address and the port`() {
        assertThat(AddressTuple("10.0.0.1", 47989).hashCode()).isEqualTo(-1321703088)
        assertThat(AddressTuple("fe80::1", 47989).hashCode()).isEqualTo(-1529018148)
        assertThat(AddressTuple("pc.local", 47989).hashCode()).isEqualTo(1241144710)
        assertThat(AddressTuple("pc.local", 47989).hashCode())
            .isEqualTo(Objects.hash("pc.local", 47989))
    }

    @Test
    fun `equal tuples hash alike, so a set holds one of them`() {
        val set = hashSetOf(AddressTuple("pc.local", 47989))

        assertThat(set.add(AddressTuple("pc.local", 47989))).isFalse()
        assertThat(set.add(AddressTuple("pc.local", 47990))).isTrue()
        assertThat(set).hasSize(2)
    }

    @Test
    fun `an IPv4 address and a host name are shown as address and port`() {
        assertThat(AddressTuple("192.168.1.5", 47989).toString()).isEqualTo("192.168.1.5:47989")
        assertThat(AddressTuple("pc.local", 47989).toString()).isEqualTo("pc.local:47989")
        assertThat(AddressTuple("", 1).toString()).isEqualTo(":1")
    }

    @Test
    fun `an IPv6 address is shown in brackets`() {
        assertThat(AddressTuple("fe80::1", 47989).toString()).isEqualTo("[fe80::1]:47989")
        assertThat(AddressTuple("[2001:db8::1]", 50000).toString()).isEqualTo("[2001:db8::1]:50000")
    }
}
