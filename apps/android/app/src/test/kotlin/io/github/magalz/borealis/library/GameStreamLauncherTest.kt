package io.github.magalz.borealis.library

import android.app.Activity
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.library.domain.StreamRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GameStreamLauncherTest {
    private val binder = FakeCmsBinder()
    private val boxArt = FakeBoxArt()
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).get()

    /** A launcher whose connection is bound and has seen every host in [hosts]. */
    private fun TestScope.launcher(
        vararg hosts: com.limelight.nvstream.http.ComputerDetails
    ): GameStreamLauncher {
        val io = UnconfinedTestDispatcher(testScheduler)
        val connection = ComputerManagerConnection(FakeCmsConnector(binder), io)
        backgroundScope.launch(io) { connection.hostUpdates().collect {} }
        hosts.forEach { binder.emit(it) }
        return GameStreamLauncher(connection, boxArt)
    }

    @Test
    fun `a known host starts the stream with the app of the request`() = runTest {
        val launcher = launcher(details(uuid = "a", rawAppList = null))

        val started =
            launcher.start(
                activity,
                StreamRequest("a", appId = 3, appName = "Game", hdrSupported = true),
            )

        assertThat(started).isTrue()
        val (target, app, computer) = binder.streams.single()
        assertThat(target).isSameInstanceAs(activity)
        assertThat(app.appId).isEqualTo(3)
        assertThat(app.appName).isEqualTo("Game")
        assertThat(app.isHdrSupported).isTrue()
        assertThat(computer.uuid).isEqualTo("a")
    }

    @Test
    fun `the HDR flag comes from the cached app list when the app is in it`() = runTest {
        val launcher = launcher(details(uuid = "a", rawAppList = APP_LIST))

        launcher.start(
            activity,
            StreamRequest("a", appId = 7, appName = "Steam", hdrSupported = false),
        )
        launcher.start(
            activity,
            StreamRequest("a", appId = 8, appName = "Desktop", hdrSupported = true),
        )

        assertThat(binder.streams.map { it.second.isHdrSupported })
            .containsExactly(true, false)
            .inOrder()
    }

    @Test
    fun `a resume without a name takes the name from the cached app list`() = runTest {
        val launcher = launcher(details(uuid = "a", rawAppList = APP_LIST))

        launcher.start(
            activity,
            StreamRequest("a", appId = 8, appName = null, hdrSupported = false),
        )

        assertThat(binder.streams.single().second.appName).isEqualTo("Desktop")
    }

    @Test
    fun `a resume of an app that no list names passes the placeholder`() = runTest {
        val launcher = launcher(details(uuid = "a", rawAppList = APP_LIST))

        launcher.start(
            activity,
            StreamRequest("a", appId = 99, appName = null, hdrSupported = false),
        )

        assertThat(binder.streams.single().second.appName).isEqualTo(UNKNOWN_APP_NAME)
    }

    @Test
    fun `an app that is not in the list keeps the flag of the request`() = runTest {
        val launcher = launcher(details(uuid = "a", rawAppList = APP_LIST))

        launcher.start(
            activity,
            StreamRequest("a", appId = 99, appName = "Other", hdrSupported = true),
        )

        assertThat(binder.streams.single().second.isHdrSupported).isTrue()
    }

    @Test
    fun `the details that reach the stream are the live ones when the service has them`() =
        runTest {
            val live = details(uuid = "a")
            binder.computers["a"] = live
            val launcher = launcher(details(uuid = "a"))

            launcher.start(
                activity,
                StreamRequest("a", appId = 3, appName = "Game", hdrSupported = false),
            )

            assertThat(binder.streams.single().third).isSameInstanceAs(live)
        }

    @Test
    fun `launching an app starts a prefetch of its box art for the TV channel`() = runTest {
        val launcher = launcher(details(uuid = "a"))

        launcher.start(
            activity,
            StreamRequest("a", appId = 3, appName = "Game", hdrSupported = false),
        )

        assertThat(boxArt.prefetched).containsExactly("a" to 3)
    }

    @Test
    fun `a stream that does not start prefetches nothing`() = runTest {
        val launcher = launcher()

        launcher.start(
            activity,
            StreamRequest("nobody", appId = 3, appName = "Game", hdrSupported = false),
        )

        assertThat(boxArt.prefetched).isEmpty()
    }

    @Test
    fun `an unknown host does not start a stream`() = runTest {
        val launcher = launcher()

        val started =
            launcher.start(
                activity,
                StreamRequest("nobody", appId = 3, appName = "Game", hdrSupported = false),
            )

        assertThat(started).isFalse()
        assertThat(binder.streams).isEmpty()
    }

    @Test
    fun `without a connection to the service nothing starts`() = runTest {
        val connection =
            ComputerManagerConnection(
                FakeCmsConnector(binder),
                UnconfinedTestDispatcher(testScheduler),
            )

        val started =
            GameStreamLauncher(connection, boxArt)
                .start(
                    activity,
                    StreamRequest("a", appId = 3, appName = "Game", hdrSupported = false),
                )

        assertThat(started).isFalse()
    }
}
