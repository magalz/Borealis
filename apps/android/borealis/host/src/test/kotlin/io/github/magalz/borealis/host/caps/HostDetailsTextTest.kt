package io.github.magalz.borealis.host.caps

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.host.R
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element

class HostDetailsTextTest {
    /** The real strings of `res/values/strings_host_details.xml`, formatted like `getString`. */
    private val english: (Int, List<Any>) -> String = run {
        val nodes =
            DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(File("src/main/res/values/strings_host_details.xml"))
                .getElementsByTagName("string")
        val byId =
            (0 until nodes.length).associate {
                val element = nodes.item(it) as Element
                R.string::class.java.getField(element.getAttribute("name")).getInt(null) to
                    element.textContent
            }
        return@run { id, args -> byId.getValue(id).format(Locale.ROOT, *args.toTypedArray()) }
    }

    /** A host that is online and paired, with the one address it is reached at and nothing else. */
    private val deskPc =
        HostDetailsFacts(
            name = "Desk PC",
            state = HostState.ONLINE,
            address = "192.168.1.20:47989",
            otherAddresses = emptyList(),
            paired = true,
            macAddress = null,
            runningApp = null,
        )

    private fun text(facts: HostDetailsFacts) = HostDetailsText.build(english, facts)

    @Test
    fun `an online host shows every fact it has, one line each`() {
        val text =
            text(
                deskPc.copy(
                    otherAddresses = listOf("203.0.113.7:47989", "[2001:db8::1]:47989"),
                    macAddress = "AA:BB:CC:DD:EE:FF",
                    runningApp = "Desktop",
                )
            )

        assertThat(text)
            .isEqualTo(
                """
                Name: Desk PC
                Status: Online
                Address: 192.168.1.20:47989
                Other addresses: 203.0.113.7:47989, [2001:db8::1]:47989
                Paired: Yes
                MAC address (for wake): AA:BB:CC:DD:EE:FF
                Running: Desktop

                """
                    .trimIndent()
            )
    }

    @Test
    fun `the status says online, offline or checking`() {
        assertThat(text(deskPc.copy(state = HostState.ONLINE))).contains("Status: Online\n")
        assertThat(text(deskPc.copy(state = HostState.OFFLINE))).contains("Status: Offline\n")
        assertThat(text(deskPc.copy(state = HostState.UNKNOWN))).contains("Status: Checking…\n")
    }

    @Test
    fun `an offline host with nothing else known shows its name, status and pairing`() {
        val text = text(deskPc.copy(state = HostState.OFFLINE, address = null, paired = true))

        assertThat(text).isEqualTo("Name: Desk PC\nStatus: Offline\nPaired: Yes\n")
    }

    @Test
    fun `a host without addresses shows no address line`() {
        val text = text(deskPc.copy(address = null, otherAddresses = emptyList()))

        assertThat(text).doesNotContain("Address")
        assertThat(text).doesNotContain("null")
    }

    @Test
    fun `without an address in use the other addresses are still listed`() {
        val text = text(deskPc.copy(address = null, otherAddresses = listOf("192.168.1.31:47989")))

        assertThat(text).doesNotContain("Address:")
        assertThat(text).contains("Other addresses: 192.168.1.31:47989\n")
    }

    @Test
    fun `the other addresses leave out the one in use and the repeats`() {
        val text =
            text(
                deskPc.copy(
                    address = "192.168.1.20:47989",
                    otherAddresses =
                        listOf(
                            "192.168.1.20:47989",
                            "203.0.113.7:47989",
                            "203.0.113.7:47989",
                            "192.168.1.20:48000",
                            "",
                        ),
                )
            )

        assertThat(text).contains("Other addresses: 203.0.113.7:47989, 192.168.1.20:48000\n")
    }

    @Test
    fun `the other addresses line is left out when the only other address is the one in use`() {
        val text = text(deskPc.copy(otherAddresses = listOf("192.168.1.20:47989")))

        assertThat(text).doesNotContain("Other addresses")
    }

    @Test
    fun `pairing says yes or no and is left out while unknown`() {
        assertThat(text(deskPc.copy(paired = true))).contains("Paired: Yes\n")
        assertThat(text(deskPc.copy(paired = false))).contains("Paired: No\n")
        assertThat(text(deskPc.copy(paired = null))).doesNotContain("Paired")
    }

    @Test
    fun `a known MAC address is shown`() {
        assertThat(text(deskPc.copy(macAddress = "aa:bb:cc:dd:ee:ff")))
            .contains("MAC address (for wake): aa:bb:cc:dd:ee:ff\n")
    }

    @Test
    fun `no MAC address, a blank one or the all-zero placeholder leaves the line out`() {
        assertThat(text(deskPc.copy(macAddress = null))).doesNotContain("MAC")
        assertThat(text(deskPc.copy(macAddress = " "))).doesNotContain("MAC")
        assertThat(text(deskPc.copy(macAddress = "00:00:00:00:00:00"))).doesNotContain("MAC")
        assertThat(text(deskPc.copy(macAddress = "00-00-00-00-00-00"))).doesNotContain("MAC")
    }

    @Test
    fun `a MAC address with a non-zero byte is not the placeholder`() {
        assertThat(text(deskPc.copy(macAddress = "00:00:00:00:00:01"))).contains("MAC address")
    }

    @Test
    fun `the running app is shown by name and left out when none is known`() {
        assertThat(text(deskPc.copy(runningApp = "Steam Big Picture")))
            .contains("Running: Steam Big Picture\n")
        assertThat(text(deskPc.copy(runningApp = null))).doesNotContain("Running")
        assertThat(text(deskPc.copy(runningApp = ""))).doesNotContain("Running")
    }

    @Test
    fun `a host without a name shows no name line`() {
        assertThat(text(deskPc.copy(name = null))).doesNotContain("Name")
        assertThat(text(deskPc.copy(name = "  "))).doesNotContain("Name")
    }

    @Test
    fun `a name that looks like a format string is shown as it is`() {
        assertThat(text(deskPc.copy(name = "100% %s PC"))).contains("Name: 100% %s PC\n")
    }

    @Test
    fun `no line is raw, null or a technical name`() {
        val text =
            text(deskPc.copy(name = null, address = null, paired = null, state = HostState.UNKNOWN))

        assertThat(text).doesNotContain("null")
        assertThat(text).doesNotContain("UNKNOWN")
        assertThat(text).doesNotContain("UUID")
        assertThat(text).doesNotContain("HTTPS Port")
    }
}
