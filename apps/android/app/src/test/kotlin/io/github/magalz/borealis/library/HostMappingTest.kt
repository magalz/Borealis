package io.github.magalz.borealis.library

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.PairingManager.PairState
import io.github.magalz.borealis.host.caps.BorealisHostInfo
import io.github.magalz.borealis.library.domain.HostCapability
import io.github.magalz.borealis.library.domain.Reachability
import io.github.magalz.borealis.library.domain.RunningApp
import io.github.magalz.borealis.nativecore.HostCaps
import io.github.magalz.borealis.nativecore.HostFamily
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HostMappingTest {
    @Test
    fun `the id, the name and the pairing come from the details`() {
        val host =
            details(uuid = "u1", name = "Den PC").reported(pairState = PairState.PAIRED).toHost()

        assertThat(host.id).isEqualTo("u1")
        assertThat(host.name).isEqualTo("Den PC")
        assertThat(host.paired).isTrue()
        assertThat(details().reported(pairState = PairState.NOT_PAIRED).toHost().paired).isFalse()
        assertThat(details().reported(pairState = PairState.FAILED).toHost().paired).isFalse()
    }

    @Test
    fun `reachability follows the state`() {
        fun reachability(state: ComputerDetails.State) =
            details().reported(state = state).toHost().reachability

        assertThat(reachability(ComputerDetails.State.ONLINE)).isEqualTo(Reachability.ONLINE)
        assertThat(reachability(ComputerDetails.State.OFFLINE)).isEqualTo(Reachability.OFFLINE)
        assertThat(reachability(ComputerDetails.State.UNKNOWN)).isEqualTo(Reachability.UNKNOWN)
    }

    @Test
    fun `a host can be woken when its MAC address is known`() {
        assertThat(details().reported(mac = "AA:BB:CC:DD:EE:FF").toHost().canWake).isTrue()
        assertThat(details().reported(mac = null).toHost().canWake).isFalse()
    }

    @Test
    fun `the running app takes its name from the cached app list`() {
        val host = details(runningGameId = 7, rawAppList = APP_LIST).toHost()

        assertThat(host.runningApp).isEqualTo(RunningApp(7, "Steam"))
    }

    @Test
    fun `the running app of a paired host has no name when the list is unknown or lacks it`() {
        assertThat(details(runningGameId = 7, rawAppList = null).toHost().runningApp)
            .isEqualTo(RunningApp(7, null))
        assertThat(details(runningGameId = 99, rawAppList = APP_LIST).toHost().runningApp)
            .isEqualTo(RunningApp(99, null))
        assertThat(details(runningGameId = 7, rawAppList = "not xml").toHost().runningApp)
            .isEqualTo(RunningApp(7, null))
    }

    @Test
    fun `a host that is not paired shows no running app`() {
        for (pairState in listOf(PairState.NOT_PAIRED, PairState.FAILED)) {
            assertThat(
                    details(runningGameId = 7).reported(pairState = pairState).toHost().runningApp
                )
                .isNull()
            assertThat(
                    details(runningGameId = 7, rawAppList = APP_LIST)
                        .reported(pairState = pairState)
                        .toHost()
                        .runningApp
                )
                .isNull()
        }
    }

    @Test
    fun `nothing runs when the host reports no running app`() {
        assertThat(details(runningGameId = 0, rawAppList = APP_LIST).toHost().runningApp).isNull()
    }

    @Test
    fun `an app list that was already parsed is used as it is`() {
        val apps = parseApps(APP_LIST)

        val host = details(runningGameId = 8, rawAppList = null).toHost(apps)

        assertThat(host.runningApp).isEqualTo(RunningApp(8, "Desktop", isDesktop = true))
    }

    @Test
    fun `the running app is Desktop when its title is Desktop`() {
        fun running(title: String) =
            details(runningGameId = 8).toHost(listOf(NvApp(title, 8, false))).runningApp?.isDesktop

        assertThat(running("Desktop")).isTrue()
        assertThat(running(" desktop ")).isTrue()
        assertThat(running("DESKTOP")).isTrue()
    }

    @Test
    fun `the running app is not Desktop when it is a game or its name is not known`() {
        fun running(title: String) =
            details(runningGameId = 8).toHost(listOf(NvApp(title, 8, false))).runningApp?.isDesktop

        assertThat(running("Steam")).isFalse()
        assertThat(running("Desktop 2")).isFalse()
        assertThat(running("My Desktop")).isFalse()
        assertThat(details(runningGameId = 7, rawAppList = null).toHost().runningApp?.isDesktop)
            .isFalse()
        assertThat(
                details(runningGameId = 99, rawAppList = APP_LIST).toHost().runningApp?.isDesktop
            )
            .isFalse()
    }

    @Test
    fun `the apps of a raw list keep their id, name and HDR flag`() {
        val apps = parseApps(APP_LIST)

        assertThat(apps.map { Triple(it.appId, it.appName, it.isHdrSupported) })
            .containsExactly(Triple(7, "Steam", true), Triple(8, "Desktop", false))
            .inOrder()
        assertThat(parseApps(null)).isEmpty()
        assertThat(parseApps("")).isEmpty()
        assertThat(parseApps("<root status_code=\"401\" status_message=\"no\"></root>")).isEmpty()
    }

    @Test
    fun `the address is the active one, else the manual, else the local one`() {
        fun address(active: String?, manual: String?, local: String?) =
            details(address = active)
                .also {
                    it.manualAddress = manual?.let { host -> AddressTuple(host, 47989) }
                    it.localAddress = local?.let { host -> AddressTuple(host, 47989) }
                }
                .toHost()
                .address

        assertThat(address("10.0.0.2", "10.0.0.3", "10.0.0.4")).isEqualTo("10.0.0.2")
        assertThat(address(null, "10.0.0.3", "10.0.0.4")).isEqualTo("10.0.0.3")
        assertThat(address(null, null, "10.0.0.4")).isEqualTo("10.0.0.4")
        assertThat(address(null, null, null)).isNull()
    }

    @Test
    fun `each capability follows its flag of the host info`() {
        fun capabilities(caps: HostCaps) = details().withInfo(caps).toHost().capabilities

        assertThat(capabilities(caps())).isEmpty()
        assertThat(capabilities(caps(hevc = true))).containsExactly(HostCapability.HEVC)
        assertThat(capabilities(caps(hevcMain10 = true)))
            .containsExactly(HostCapability.HEVC_10_BIT)
        assertThat(capabilities(caps(av1 = true))).containsExactly(HostCapability.AV1)
        assertThat(capabilities(caps(av1Main10 = true))).containsExactly(HostCapability.AV1_10_BIT)
        assertThat(capabilities(caps(yuv444 = true))).containsExactly(HostCapability.YUV_444)
        assertThat(capabilities(caps(hevc = true, hevcMain10 = true, av1 = true)))
            .containsExactly(
                HostCapability.HEVC,
                HostCapability.HEVC_10_BIT,
                HostCapability.AV1,
            )
    }

    @Test
    fun `HDR is a capability when any app of the list supports it`() {
        val withInfo = details(rawAppList = APP_LIST).withInfo(caps())

        assertThat(withInfo.toHost().capabilities).containsExactly(HostCapability.HDR)
        assertThat(details(rawAppList = APP_LIST).withInfo(caps(hevc = true)).toHost().capabilities)
            .containsExactly(HostCapability.HEVC, HostCapability.HDR)
        assertThat(
                details(rawAppList = APP_LIST_WITHOUT_HDR)
                    .withInfo(caps(hevc = true))
                    .toHost()
                    .capabilities
            )
            .containsExactly(HostCapability.HEVC)
        assertThat(details(rawAppList = null).withInfo(caps(hevc = true)).toHost().capabilities)
            .containsExactly(HostCapability.HEVC)
    }

    @Test
    fun `HDR is read from an app list that was already parsed`() {
        val host = details(rawAppList = null).withInfo(caps()).toHost(parseApps(APP_LIST))

        assertThat(host.capabilities).containsExactly(HostCapability.HDR)
    }

    @Test
    fun `a host without host info has no capabilities, even with an HDR app`() {
        val host = details(rawAppList = APP_LIST).toHost()

        assertThat(host.capabilities).isEmpty()
    }

    @Test
    fun `a bracketed IPv6 address is shown without the brackets`() {
        val host =
            details(address = null)
                .also { it.manualAddress = AddressTuple("[fe80::1]", 47989) }
                .toHost()

        assertThat(host.address).isEqualTo("fe80::1")
    }

    @Test
    fun `the same raw app list is parsed once across updates`() {
        val parsed = mutableListOf<String?>()
        val appLists = ParsedAppLists { raw ->
            parsed += raw
            parseApps(raw)
        }
        val host = details(rawAppList = APP_LIST).withInfo(caps(hevc = true))

        repeat(3) { host.toHost(appLists = appLists) }

        assertThat(parsed).containsExactly(APP_LIST)
        assertThat(host.toHost(appLists = appLists).capabilities)
            .containsExactly(HostCapability.HEVC, HostCapability.HDR)
    }

    @Test
    fun `a changed app list is parsed again`() {
        val parsed = mutableListOf<String?>()
        val appLists = ParsedAppLists { raw ->
            parsed += raw
            parseApps(raw)
        }

        details(rawAppList = APP_LIST).withInfo(caps()).toHost(appLists = appLists)
        details(rawAppList = APP_LIST_WITHOUT_HDR).withInfo(caps()).toHost(appLists = appLists)

        assertThat(parsed).containsExactly(APP_LIST, APP_LIST_WITHOUT_HDR).inOrder()
    }

    @Test
    fun `an unreadable app list is parsed once, not at every update`() {
        val parsed = mutableListOf<String?>()
        val appLists = ParsedAppLists { raw ->
            parsed += raw
            parseApps(raw)
        }
        val host = details(rawAppList = "not xml").withInfo(caps())

        repeat(3) { host.toHost(appLists = appLists) }

        assertThat(parsed).hasSize(1)
    }

    @Test
    fun `an offline host neither parses its app list nor shows HDR`() {
        val parsed = mutableListOf<String?>()
        val appLists = ParsedAppLists { raw ->
            parsed += raw
            parseApps(raw)
        }

        val host =
            details(rawAppList = APP_LIST)
                .reported(state = ComputerDetails.State.OFFLINE)
                .withInfo(caps(hevc = true))
                .toHost(appLists = appLists)

        assertThat(parsed).isEmpty()
        assertThat(host.capabilities).containsExactly(HostCapability.HEVC)
    }

    private fun ComputerDetails.withInfo(caps: HostCaps) = also {
        it.borealis = BorealisHostInfo(caps, httpsPort = 47984, httpPort = 47989)
    }

    private fun caps(
        hevc: Boolean = false,
        hevcMain10: Boolean = false,
        av1: Boolean = false,
        av1Main10: Boolean = false,
        yuv444: Boolean = false,
    ) =
        HostCaps(
            HostFamily.SUNSHINE,
            listOf(7, 1, 431, -1),
            hevc,
            hevcMain10,
            av1,
            av1Main10,
            yuv444,
            emptySet(),
        )

    private companion object {
        const val APP_LIST_WITHOUT_HDR =
            """<?xml version="1.0" encoding="utf-8"?><root status_code="200">""" +
                """<App><IsHdrSupported>0</IsHdrSupported><AppTitle>Desktop</AppTitle><ID>8</ID></App>""" +
                """</root>"""
    }
}
