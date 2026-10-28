package io.github.magalz.borealis.library

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowDialog

/** The thin adapters over upstream classes that need no service and no native library. */
@RunWith(RobolectricTestRunner::class)
class UpstreamSeamsTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val activity = Robolectric.buildActivity(Activity::class.java).create().get()

    @Test
    fun `the local networks are site-local IPv4 addresses with a prefix length`() {
        val networks = DeviceLocalNetworks().siteLocalIpv4()

        assertThat(networks.all { it.address.isSiteLocalAddress }).isTrue()
        assertThat(networks.all { it.prefixLength in 0..32 }).isTrue()
    }

    @Test
    fun `wake packets are sent to the addresses of a host`() {
        val host =
            details().reported(mac = "AA:BB:CC:DD:EE:FF").also {
                it.manualAddress = AddressTuple("127.0.0.1", 47989)
            }

        UpstreamWakeSender().send(host)
    }

    @Test
    fun `deleting the box art of a host removes its cached files and keeps the others`() {
        val mine = File(app.cacheDir, "boxart/host-1").also { it.mkdirs() }
        val other = File(app.cacheDir, "boxart/host-2").also { it.mkdirs() }
        File(mine, "7.png").writeText("art")
        File(other, "7.png").writeText("art")

        DiskBoxArtCache(app).delete("host-1")

        assertThat(mine.listFiles()).isEmpty()
        assertThat(other.listFiles()).hasLength(1)
    }

    // The test pins which dispatcher the module provides: it has to name it.
    @Suppress("InjectDispatcher")
    @Test
    fun `blocking work runs on the IO dispatcher`() {
        assertThat(LibraryAdapterModule.ioDispatcher()).isSameInstanceAs(Dispatchers.IO)
    }

    @Test
    fun `the decoder crash notice shows nothing when no crash was recorded`() {
        UpstreamLauncherStartup().showDecoderCrashDialog(activity)

        assertThat(ShadowDialog.getLatestDialog()).isNull()
    }

    @Test
    fun `the decoder crash notice has an OK button and no Help button`() {
        activity.getSharedPreferences("DecoderTombstone", 0).edit().putInt("CrashCount", 1).commit()

        UpstreamLauncherStartup().showDecoderCrashDialog(activity)

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertThat(dialog.getButton(AlertDialog.BUTTON_POSITIVE).visibility).isEqualTo(View.VISIBLE)
        assertThat(dialog.getButton(AlertDialog.BUTTON_NEUTRAL).visibility).isEqualTo(View.GONE)
        assertThat(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).visibility).isEqualTo(View.GONE)
    }

    @Test
    fun `a host client is built for a host with an active address`() {
        val client = NvHostClientFactory(app).create(details(address = "192.168.1.5"), "unique")

        assertThat(client).isInstanceOf(NvHostClient::class.java)
    }

    @Test
    fun `a host client cannot be built for a host without an active address`() {
        try {
            NvHostClientFactory(app).create(details(address = null), "unique")
            throw AssertionError("a client was built without an address")
        } catch (expected: IOException) {
            assertThat(expected).hasMessageThat().contains("No active address")
        }
    }
}
