package io.github.magalz.borealis.library

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.http.ComputerDetails
import io.github.magalz.borealis.host.caps.BorealisHostInfo
import io.github.magalz.borealis.library.domain.HostCapability
import io.github.magalz.borealis.library.domain.RunningApp
import io.github.magalz.borealis.nativecore.HostCaps
import io.github.magalz.borealis.nativecore.HostFamily
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * After a cold start no app list has been polled yet, but the computer manager service cached the
 * last one on disk. The first sight of a host without a list seeds it from that cache, off the main
 * thread, and a fresh poll replaces it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ComputerManagerConnectionCacheTest {
    private val binder = FakeCmsBinder()
    private val reads = CopyOnWriteArrayList<Pair<String, String>>()
    private var cached: String? = APP_LIST

    private val cache = AppListCache { hostId ->
        reads += hostId to Thread.currentThread().name
        cached
    }

    private fun connection(io: kotlinx.coroutines.CoroutineDispatcher) =
        ComputerManagerConnection(FakeCmsConnector(binder), io, cache)

    private fun ComputerDetails.withInfo() = also {
        it.borealis =
            BorealisHostInfo(
                HostCaps(
                    HostFamily.SUNSHINE,
                    listOf(7, 1, 431, -1),
                    true,
                    false,
                    false,
                    false,
                    false,
                    emptySet(),
                ),
                httpsPort = 47984,
                httpPort = 47989,
            )
    }

    private fun kotlinx.coroutines.test.TestScope.collecting(
        connection: ComputerManagerConnection
    ) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            connection.hostUpdates().collect {}
        }

    @Test
    fun `a host first seen without a list gets the cached list, so its running app has a name and HDR`() =
        runTest {
            val connection = connection(UnconfinedTestDispatcher(testScheduler))
            collecting(connection)

            binder.emit(details(uuid = "a", runningGameId = 7, rawAppList = null).withInfo())

            val seeded = connection.updates.value.getValue("a")
            assertThat(seeded.rawAppList).isEqualTo(APP_LIST)
            val host = seeded.toHost()
            assertThat(host.runningApp).isEqualTo(RunningApp(7, "Steam"))
            assertThat(host.capabilities).contains(HostCapability.HDR)
        }

    @Test
    fun `the cache is read once for a host however many updates follow`() = runTest {
        val connection = connection(UnconfinedTestDispatcher(testScheduler))
        collecting(connection)

        binder.emit(details(uuid = "a", rawAppList = null))
        binder.emit(details(uuid = "a", rawAppList = null))
        binder.emit(
            details(uuid = "a", rawAppList = null).reported(state = ComputerDetails.State.OFFLINE)
        )

        assertThat(reads.map { it.first }).containsExactly("a")
        assertThat(connection.updates.value.getValue("a").rawAppList).isEqualTo(APP_LIST)
    }

    @Test
    fun `each host reads its own cache`() = runTest {
        val connection = connection(UnconfinedTestDispatcher(testScheduler))
        collecting(connection)

        binder.emit(details(uuid = "a", rawAppList = null))
        binder.emit(details(uuid = "b", rawAppList = null))

        assertThat(reads.map { it.first }).containsExactly("a", "b")
    }

    @Test
    fun `a list that the service already has is kept and the cache is not read`() = runTest {
        val connection = connection(UnconfinedTestDispatcher(testScheduler))
        collecting(connection)

        binder.emit(details(uuid = "a", rawAppList = OTHER_LIST))

        assertThat(reads).isEmpty()
        assertThat(connection.updates.value.getValue("a").rawAppList).isEqualTo(OTHER_LIST)
    }

    @Test
    fun `a fresh poll replaces the cached list`() = runTest {
        val connection = connection(UnconfinedTestDispatcher(testScheduler))
        collecting(connection)
        binder.emit(details(uuid = "a", rawAppList = null))

        binder.emit(details(uuid = "a", rawAppList = OTHER_LIST))

        assertThat(connection.updates.value.getValue("a").rawAppList).isEqualTo(OTHER_LIST)
    }

    @Test
    fun `a host without a cache stays without a list and is not read again`() = runTest {
        cached = null
        val connection = connection(UnconfinedTestDispatcher(testScheduler))
        collecting(connection)

        binder.emit(details(uuid = "a", rawAppList = null))
        binder.emit(details(uuid = "a", rawAppList = null))

        assertThat(connection.updates.value.getValue("a").rawAppList).isNull()
        assertThat(reads).hasSize(1)
    }

    @Test
    fun `a host that the user adds is seeded on the IO dispatcher, not on the caller`() = runTest {
        val executor = Executors.newSingleThreadExecutor { Thread(it, "io-test") }
        try {
            val connection = connection(executor.asCoroutineDispatcher())

            connection.addComputer(details(uuid = "a", rawAppList = null))

            assertThat(reads.map { it.second }).containsExactly("io-test")
            assertThat(connection.updates.value.getValue("a").rawAppList).isEqualTo(APP_LIST)
        } finally {
            executor.shutdownNow()
        }
    }

    private companion object {
        const val OTHER_LIST =
            """<?xml version="1.0" encoding="utf-8"?><root status_code="200">""" +
                """<App><IsHdrSupported>0</IsHdrSupported><AppTitle>Other</AppTitle><ID>9</ID></App>""" +
                """</root>"""
    }
}
