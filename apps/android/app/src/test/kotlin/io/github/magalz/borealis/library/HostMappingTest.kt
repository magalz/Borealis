package io.github.magalz.borealis.library

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.PairingManager.PairState
import io.github.magalz.borealis.library.domain.Reachability
import io.github.magalz.borealis.library.domain.RunningApp
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HostMappingTest {
    @Test
    fun `the id, the name and the pairing come from the details`() {
        val host = details(uuid = "u1", name = "Den PC", pairState = PairState.PAIRED).toHost()

        assertThat(host.id).isEqualTo("u1")
        assertThat(host.name).isEqualTo("Den PC")
        assertThat(host.paired).isTrue()
        assertThat(details(pairState = PairState.NOT_PAIRED).toHost().paired).isFalse()
        assertThat(details(pairState = PairState.FAILED).toHost().paired).isFalse()
    }

    @Test
    fun `reachability follows the state`() {
        fun reachability(state: ComputerDetails.State) =
            details(state = state).toHost().reachability

        assertThat(reachability(ComputerDetails.State.ONLINE)).isEqualTo(Reachability.ONLINE)
        assertThat(reachability(ComputerDetails.State.OFFLINE)).isEqualTo(Reachability.OFFLINE)
        assertThat(reachability(ComputerDetails.State.UNKNOWN)).isEqualTo(Reachability.UNKNOWN)
    }

    @Test
    fun `a host can be woken when its MAC address is known`() {
        assertThat(details(mac = "AA:BB:CC:DD:EE:FF").toHost().canWake).isTrue()
        assertThat(details(mac = null).toHost().canWake).isFalse()
    }

    @Test
    fun `the running app takes its name from the cached app list`() {
        val host = details(runningGameId = 7, rawAppList = APP_LIST).toHost()

        assertThat(host.runningApp).isEqualTo(RunningApp(7, "Steam"))
    }

    @Test
    fun `the running app is called app when the list is unknown or lacks it`() {
        assertThat(details(runningGameId = 7, rawAppList = null).toHost().runningApp)
            .isEqualTo(RunningApp(7, "app"))
        assertThat(details(runningGameId = 99, rawAppList = APP_LIST).toHost().runningApp)
            .isEqualTo(RunningApp(99, "app"))
        assertThat(details(runningGameId = 7, rawAppList = "not xml").toHost().runningApp)
            .isEqualTo(RunningApp(7, "app"))
    }

    @Test
    fun `nothing runs when the host reports no running app`() {
        assertThat(details(runningGameId = 0, rawAppList = APP_LIST).toHost().runningApp).isNull()
    }

    @Test
    fun `an app list that was already parsed is used as it is`() {
        val apps = parseApps(APP_LIST)

        val host = details(runningGameId = 8, rawAppList = null).toHost(apps)

        assertThat(host.runningApp).isEqualTo(RunningApp(8, "Desktop"))
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
}
