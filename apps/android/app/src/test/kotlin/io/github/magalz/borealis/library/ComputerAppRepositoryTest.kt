package io.github.magalz.borealis.library

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.HostHttpResponseException
import com.limelight.nvstream.http.PairingManager.PairState
import io.github.magalz.borealis.library.domain.App
import io.github.magalz.borealis.library.domain.AppsSnapshot
import io.github.magalz.borealis.library.domain.QuitResult
import io.github.magalz.borealis.library.domain.Reachability
import java.io.FileNotFoundException
import java.io.IOException
import java.net.UnknownHostException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ComputerAppRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val binder = FakeCmsBinder()
    private val clients = FakeHostClients(binder)
    private val prefs = context.getSharedPreferences("HiddenApps", Context.MODE_PRIVATE)
    private val hiddenApps = HiddenApps(context)
    private val shortcuts = FakeHiddenAppShortcuts()

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
    }

    private fun TestScope.repository(): ComputerAppRepository {
        val io = UnconfinedTestDispatcher(testScheduler)
        return ComputerAppRepository(
            ComputerManagerConnection(FakeCmsConnector(binder), io),
            clients,
            hiddenApps,
            FakeTexts(),
            shortcuts,
            io,
        )
    }

    private fun TestScope.collect(
        repository: ComputerAppRepository,
        hostId: String = "host-1",
    ): Pair<List<AppsSnapshot>, kotlinx.coroutines.Job> {
        val seen = mutableListOf<AppsSnapshot>()
        val job =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                repository.apps(hostId).collect { seen += it }
            }
        return seen to job
    }

    @Test
    fun `the apps are loading until the first list arrives, then they are the parsed list`() =
        runTest {
            val live = details(rawAppList = null)
            binder.computers["host-1"] = live
            val (seen, job) = collect(repository())
            assertThat(seen.last().loading).isTrue()
            assertThat(seen.last().apps).isEmpty()
            assertThat(seen.last().host?.name).isEqualTo("Den PC")

            live.rawAppList = APP_LIST
            binder.emit(live)

            assertThat(seen.last().loading).isFalse()
            assertThat(seen.last().apps)
                .containsExactly(App(7, "Steam", true, false), App(8, "Desktop", false, false))
                .inOrder()
            job.cancelAndJoin()
        }

    @Test
    fun `the list stays once received when the host poll resets the service's details`() = runTest {
        val live = details(rawAppList = null)
        binder.computers["host-1"] = live
        val (seen, job) = collect(repository())

        // The app list poller stores the list in the service's details and reports them.
        live.rawAppList = APP_LIST
        binder.emit(live)
        // The host poll of the service then updates the same details from a fresh poll result,
        // which has no app list: `ComputerDetails.update` copies that null over the list.
        live.update(details(rawAppList = null))
        binder.emit(live)

        assertThat(live.rawAppList).isNull()
        assertThat(seen.last().loading).isFalse()
        assertThat(seen.last().apps.map { it.name }).containsExactly("Steam", "Desktop").inOrder()
        job.cancelAndJoin()
    }

    @Test
    fun `an app that the user hid is marked hidden`() = runTest {
        prefs.edit().putStringSet("host-1", setOf("8")).commit()
        binder.computers["host-1"] = details(rawAppList = APP_LIST)

        val (seen, job) = collect(repository())

        assertThat(seen.last().apps.map { it.id to it.hidden })
            .containsExactly(7 to false, 8 to true)
            .inOrder()
        job.cancelAndJoin()
    }

    @Test
    fun `hiding an app updates the list that is being collected and the shared file`() = runTest {
        binder.computers["host-1"] = details(rawAppList = APP_LIST)
        val repository = repository()
        val (seen, job) = collect(repository)

        repository.setHidden("host-1", 7, hidden = true)

        assertThat(seen.last().apps.first { it.id == 7 }.hidden).isTrue()
        assertThat(prefs.getStringSet("host-1", null)).containsExactly("7")

        repository.setHidden("host-1", 7, hidden = false)

        assertThat(seen.last().apps.first { it.id == 7 }.hidden).isFalse()
        job.cancelAndJoin()
    }

    @Test
    fun `hiding an app disables its shortcut and TV program, and showing it enables the shortcut`() =
        runTest {
            val repository = repository()

            repository.setHidden("host-1", 7, hidden = true)
            assertThat(shortcuts.calls).containsExactly("hidden host-1 7")

            repository.setHidden("host-1", 7, hidden = false)
            assertThat(shortcuts.calls)
                .containsExactly("hidden host-1 7", "shown host-1 7")
                .inOrder()
        }

    @Test
    fun `the hidden app is saved even when the launcher fails to switch its shortcut`() = runTest {
        shortcuts.failure = IllegalStateException("launcher is gone")

        repository().setHidden("host-1", 7, hidden = true)

        assertThat(prefs.getStringSet("host-1", null)).containsExactly("7")
    }

    @Test
    fun `the host of the snapshot knows the running app from the list`() = runTest {
        binder.computers["host-1"] = details(rawAppList = APP_LIST, runningGameId = 7)

        val (seen, job) = collect(repository())

        assertThat(seen.last().host?.runningApp?.name).isEqualTo("Steam")
        assertThat(seen.last().host?.reachability).isEqualTo(Reachability.ONLINE)
        job.cancelAndJoin()
    }

    @Test
    fun `a host that is no longer known has no host and no apps, and is not loading`() = runTest {
        val (seen, job) = collect(repository(), hostId = "gone")

        assertThat(seen.last())
            .isEqualTo(AppsSnapshot(host = null, apps = emptyList(), loading = false))
        job.cancelAndJoin()
    }

    @Test
    fun `a list that cannot be read leaves no apps and is not loading`() = runTest {
        binder.computers["host-1"] = details(rawAppList = "<root>")

        val (seen, job) = collect(repository())

        assertThat(seen.last().apps).isEmpty()
        assertThat(seen.last().loading).isFalse()
        job.cancelAndJoin()
    }

    @Test
    fun `an update that changes nothing the screen shows is not emitted again`() = runTest {
        val live = details(rawAppList = APP_LIST)
        binder.computers["host-1"] = live
        val (seen, job) = collect(repository())
        val count = seen.size

        binder.emit(live)
        binder.emit(live)

        assertThat(seen).hasSize(count)
        job.cancelAndJoin()
    }

    @Test
    fun `the app list is polled while it is collected and not afterwards`() = runTest {
        binder.computers["host-1"] = details()
        val (_, job) = collect(repository())

        val poller = binder.pollers.single()
        assertThat(poller.started).isEqualTo(1)
        assertThat(poller.stopped).isEqualTo(0)

        job.cancelAndJoin()

        assertThat(poller.stopped).isEqualTo(1)
        assertThat(binder.events).containsExactly("startPolling", "stopPolling").inOrder()
    }

    @Test
    fun `quitting asks the host to quit the running app`() = runTest {
        binder.computers["host-1"] = details(runningGameId = 7)
        var asked = false
        clients.client.quit = {
            asked = true
            true
        }

        assertThat(repository().quit("host-1")).isEqualTo(QuitResult.Quit)
        assertThat(asked).isTrue()
    }

    @Test
    fun `a host that did not quit the app gives the generic failure`() = runTest {
        binder.computers["host-1"] = details(runningGameId = 7)
        clients.client.quit = { false }

        assertThat(repository().quit("host-1")).isEqualTo(QuitResult.Failed(null))
    }

    @Test
    fun `an app started by another device cannot be quit, and the text says so`() = runTest {
        binder.computers["host-1"] = details(runningGameId = 7)
        clients.client.quit = { throw HostHttpResponseException(599, "") }

        val result = repository().quit("host-1")

        assertThat(result).isInstanceOf(QuitResult.Failed::class.java)
        val message = (result as QuitResult.Failed).message
        assertThat(message).contains("This session wasn't started by this device")
        assertThat(message).contains("(Error code: 599)")
    }

    @Test
    fun `other errors of the host show their message`() = runTest {
        binder.computers["host-1"] = details(runningGameId = 7)
        clients.client.quit = { throw HostHttpResponseException(503, "busy") }

        assertThat(repository().quit("host-1"))
            .isEqualTo(QuitResult.Failed("Host PC returned error: busy (Error code: 503)"))
    }

    @Test
    fun `network errors show the upstream texts`() = runTest {
        binder.computers["host-1"] = details(runningGameId = 7)
        val repository = repository()

        clients.client.quit = { throw UnknownHostException("pc") }
        assertThat(repository.quit("host-1")).isEqualTo(QuitResult.Failed("unknown host"))

        clients.client.quit = { throw FileNotFoundException("/cancel") }
        assertThat(repository.quit("host-1")).isEqualTo(QuitResult.Failed("not found"))

        clients.client.quit = { throw IOException("reset") }
        assertThat(repository.quit("host-1")).isEqualTo(QuitResult.Failed("reset"))
    }

    @Test
    fun `quitting on an unknown or unreachable host fails`() = runTest {
        val repository = repository()
        assertThat(repository.quit("nobody")).isEqualTo(QuitResult.Failed(null))

        binder.computers["host-1"] =
            details().reported(state = ComputerDetails.State.ONLINE, pairState = PairState.PAIRED)
        clients.failure = IOException("No active address")
        assertThat(repository.quit("host-1")).isEqualTo(QuitResult.Failed("No active address"))
    }
}
