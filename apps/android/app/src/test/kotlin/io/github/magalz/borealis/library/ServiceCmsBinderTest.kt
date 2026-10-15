package io.github.magalz.borealis.library

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.Game
import com.limelight.computers.ComputerManagerService
import com.limelight.discovery.DiscoveryService
import com.limelight.nvstream.http.NvApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The real computer manager service and discovery service, driven by Robolectric. */
@RunWith(RobolectricTestRunner::class)
class ServiceCmsBinderTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val looper = shadowOf(Looper.getMainLooper())
    private lateinit var binder: CmsBinder

    @Before
    fun connect() {
        val discovery = Robolectric.buildService(DiscoveryService::class.java).create().get()
        shadowOf(app)
            .setComponentNameAndServiceForBindService(
                ComponentName(app, DiscoveryService::class.java),
                discovery.onBind(Intent()),
            )
        val service = Robolectric.buildService(ComputerManagerService::class.java).create().get()
        looper.idle()
        shadowOf(app)
            .setComponentNameAndServiceForBindService(
                ComponentName(app, ComputerManagerService::class.java),
                service.onBind(Intent()),
            )

        // The connector waits on a background thread; the service callbacks need the main looper.
        val connecting = CoroutineScope(Dispatchers.Default).async { ServiceCmsConnector(app).connect() }
        while (!connecting.isCompleted) {
            looper.idle()
            Thread.sleep(POLL_MS)
        }
        binder = runBlocking { connecting.await() }
    }

    @Test
    fun `the connector returns the binder of the service once it is ready`() {
        assertThat(binder.uniqueId).matches("[0-9a-f]{16}")
        assertThat(binder.getComputer("unknown")).isNull()
    }

    @Test
    fun `a poller for a host can be created and stopped before it started`() {
        val poller = binder.createAppListPoller(details(uuid = "a"))

        poller.stop()
    }

    @Test
    fun `polling can be stopped and awaited, and a host that is not known can be removed`() {
        binder.stopPolling()
        binder.waitForPollingStopped()
        binder.invalidateStateForComputer("unknown")

        binder.removeComputer(details(uuid = "unknown"))
    }

    @Test
    fun `starting a stream opens the stream activity with the host and the app`() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val computer = details(uuid = "host-1", name = "Den PC", address = "192.168.1.5")

        binder.startStream(activity, NvApp("Steam", 7, true), computer)

        val intent = shadowOf(activity).nextStartedActivity
        assertThat(intent.component?.className).isEqualTo(Game::class.java.name)
        assertThat(intent.getStringExtra(Game.EXTRA_HOST)).isEqualTo("192.168.1.5")
        assertThat(intent.getIntExtra(Game.EXTRA_PORT, 0)).isEqualTo(47989)
        assertThat(intent.getIntExtra(Game.EXTRA_HTTPS_PORT, 0)).isEqualTo(47984)
        assertThat(intent.getStringExtra(Game.EXTRA_APP_NAME)).isEqualTo("Steam")
        assertThat(intent.getIntExtra(Game.EXTRA_APP_ID, 0)).isEqualTo(7)
        assertThat(intent.getBooleanExtra(Game.EXTRA_APP_HDR, false)).isTrue()
        assertThat(intent.getStringExtra(Game.EXTRA_UNIQUEID)).isEqualTo(binder.uniqueId)
        assertThat(intent.getStringExtra(Game.EXTRA_PC_UUID)).isEqualTo("host-1")
        assertThat(intent.getStringExtra(Game.EXTRA_PC_NAME)).isEqualTo("Den PC")
    }

    private companion object {
        const val POLL_MS = 20L
    }
}
