package io.github.magalz.borealis.library

import android.app.Application
import android.content.ComponentName
import android.os.Binder
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.computers.ComputerManagerService
import io.github.magalz.borealis.testing.WorkerDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ServiceCmsConnectorTest {
    @get:Rule val workers = WorkerDispatcherRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `cancelling while the service is not connected yet unbinds the pending connection`() {
        shadowOf(app)
            .setComponentNameAndServiceForBindService(
                ComponentName(app, ComputerManagerService::class.java),
                Binder(),
            )
        val connecting =
            CoroutineScope(workers.dispatcher).launch {
                ServiceCmsConnector(app, workers.dispatcher) {}.connect()
            }
        // The service callback needs the main looper, which this test never runs.
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (
            shadowOf(app).boundServiceConnections.isEmpty() && System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(POLL_MS)
        }
        assertThat(shadowOf(app).boundServiceConnections).hasSize(1)

        connecting.cancel()
        runBlocking { connecting.join() }

        assertThat(shadowOf(app).unboundServiceConnections).hasSize(1)
    }

    private companion object {
        const val WAIT_MS = 5_000L
        const val POLL_MS = 10L
    }
}
