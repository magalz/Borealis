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
import io.github.magalz.borealis.testing.WorkerDispatcherRule
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The real computer manager service and discovery service, driven by Robolectric. */
@RunWith(RobolectricTestRunner::class)
class ServiceCmsBinderTest {
    @get:Rule val log = RecordingLog()
    @get:Rule val workers = WorkerDispatcherRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val looper = shadowOf(Looper.getMainLooper())
    private lateinit var binder: CmsBinder

    @Before
    fun connect() {
        binder = connectWith {}
    }

    /** Binds the real services and returns what the connector returns when [warmUp] is its seam. */
    private fun connectWith(warmUp: CryptoWarmUp): CmsBinder {
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
        val connecting =
            CoroutineScope(workers.dispatcher).async {
                ServiceCmsConnector(app, workers.dispatcher, warmUp).connect()
            }
        while (!connecting.isCompleted) {
            looper.idle()
            Thread.sleep(POLL_MS)
        }
        return runBlocking { connecting.await() }
    }

    @Test
    fun `the connector returns the binder of the service once it is ready`() {
        assertThat(binder.uniqueId).matches("[0-9a-f]{16}")
        assertThat(binder.getComputer("unknown")).isNull()
    }

    @Test
    fun `the connector returns before a slow warm-up finishes and runs it off the main thread`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = AtomicBoolean(false)
        val warmUpThread = AtomicReference<Thread>()

        // Gives up after a few seconds, so a connector that waits for it fails instead of hanging.
        val returned = connectWith {
            warmUpThread.set(Thread.currentThread())
            started.countDown()
            release.await(SLOW_WARM_UP_SECONDS, TimeUnit.SECONDS)
            finished.set(true)
        }

        assertThat(returned.uniqueId).matches("[0-9a-f]{16}")
        assertThat(finished.get()).isFalse()
        assertThat(started.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue()
        assertThat(warmUpThread.get()).isNotSameInstanceAs(Looper.getMainLooper().thread)
        release.countDown()
    }

    @Test
    fun `a warm-up that throws neither fails the connection nor leaks more than its class`() {
        val returned = connectWith { throw IllegalStateException("secret detail") }

        assertThat(returned.uniqueId).matches("[0-9a-f]{16}")
        val deadline = System.currentTimeMillis() + WAIT_SECONDS * 1_000
        while (
            log.records.none { it.message == "warm-up failed" } &&
                System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(POLL_MS)
        }
        val logged = log.records.single { it.message == "warm-up failed" }
        assertThat(logged.fields).containsExactly("error" to "IllegalStateException")
        assertThat(logged.toString()).doesNotContain("secret detail")
        assertThat(logged.stackTrace).isNull()
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

        binder.startStream(activity, NvApp("Steam", 7, true), computer, resumeChosen = false)

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
        const val SLOW_WARM_UP_SECONDS = 3L
        const val WAIT_SECONDS = 5L
    }
}
