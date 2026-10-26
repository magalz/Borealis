package io.github.magalz.borealis.settings.audit

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import io.github.magalz.borealis.logging.BorealisAudit
import io.github.magalz.borealis.logging.Redactor
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.testing.FakeSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UC-007: the entries of the trail as the screen shows them, masked and opened. */
@OptIn(ExperimentalCoroutinesApi::class)
class AuditViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val trail = FakeAuditTrail()
    private val settings = FakeSettingsRepository()
    private val redactor = Redactor(maskAddresses = false)

    // Lazy: a ViewModel must be created after the rule has set the main dispatcher.
    private val viewModel by lazy {
        AuditViewModel(trail, settings, fixedFormatter(), UnconfinedTestDispatcher())
    }

    @Before
    fun installRedactor() {
        BorealisAudit.install({}, redactor, "1.0.0")
    }

    @After fun uninstallRedactor() = BorealisAudit.uninstall()

    private fun TestScope.subscribe(): Job =
        backgroundScope
            .launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            .also { runCurrent() }

    private fun rows() = viewModel.uiState.value.entries

    /** Opens the entry at [index] of the list the way the screen does: by its key. */
    private fun open(index: Int) = viewModel.onEntrySelected(rows()[index].key)

    /** The summary of the only entry; fails with a plain message when there is not exactly one. */
    private fun summary(): String? {
        assertThat(viewModel.uiState.value.entries).hasSize(1)
        return viewModel.uiState.value.entries.first().summary
    }

    private fun openDetail(): AuditDetail {
        val detail = viewModel.uiState.value.detail
        assertThat(detail).isNotNull()
        return detail!!
    }

    /** What the app does when the user flips the switch: the redactor follows, then the setting. */
    private suspend fun switchMasking(on: Boolean) {
        redactor.maskAddresses = on
        settings.set(Settings.MASK_ADDRESSES, on)
    }

    @Test
    fun `the state is loading until the trail reported`() = runTest {
        val state = viewModel.uiState

        assertThat(state.value.loading).isTrue()
        assertThat(state.value.entries).isEmpty()

        subscribe()
        runCurrent()

        assertThat(state.value.loading).isFalse()
    }

    @Test
    fun `an empty trail shows no entries and no loading`() = runTest {
        subscribe()
        runCurrent()

        assertThat(viewModel.uiState.value.entries).isEmpty()
        assertThat(viewModel.uiState.value.loading).isFalse()
    }

    @Test
    fun `the entries keep the order of the trail with type time and summary`() = runTest {
        trail.entries.value =
            listOf(
                entry(AuditType.SESSION_END, "2026-10-16T20:42:00Z", "Desk PC", "Desktop"),
                entry(AuditType.PAIRING_SUCCESS, "2026-10-12T18:02:00Z", "Studio", null),
                entry(AuditType.TLS_FAILURE, "2026-10-12T18:01:00Z", null, "handshake"),
                entry(AuditType.PAIRING_START, "2026-10-12T18:00:00Z", null, null),
            )
        subscribe()
        runCurrent()

        assertThat(rows().map { Triple(it.type, it.time, it.summary) })
            .containsExactly(
                Triple(AuditType.SESSION_END, "20:42", "Desk PC · Desktop"),
                Triple(AuditType.PAIRING_SUCCESS, "Mon 18:02", "Studio"),
                Triple(AuditType.TLS_FAILURE, "Mon 18:01", "handshake"),
                Triple(AuditType.PAIRING_START, "Mon 18:00", null),
            )
            .inOrder()
    }

    @Test
    fun `a new entry in the trail appears at once`() = runTest {
        subscribe()
        runCurrent()

        trail.entries.value = listOf(entry(AuditType.LAUNCH_ATTEMPT))
        runCurrent()

        assertThat(viewModel.uiState.value.entries.map { it.type })
            .containsExactly(AuditType.LAUNCH_ATTEMPT)
    }

    @Test
    fun `addresses show while masking is off`() = runTest {
        trail.entries.value = listOf(entry(host = "Desk PC (192.168.1.20)", detail = "to 10.0.0.7"))
        subscribe()
        runCurrent()

        assertThat(summary()).isEqualTo("Desk PC (192.168.1.20) · to 10.0.0.7")
    }

    @Test
    fun `masking hides the addresses of the host and of the detail in the list`() = runTest {
        trail.entries.value = listOf(entry(host = "Desk PC (192.168.1.20)", detail = "to 10.0.0.7"))
        subscribe()
        runCurrent()

        switchMasking(true)
        runCurrent()

        val summary = summary()
        assertThat(summary).doesNotContain("192.168.1.20")
        assertThat(summary).doesNotContain("10.0.0.7")
        assertThat(summary).contains("[IP]")
    }

    @Test
    fun `masking hides the addresses in the opened entry`() = runTest {
        trail.entries.value = listOf(entry(host = "192.168.1.20", detail = "to 10.0.0.7"))
        subscribe()
        runCurrent()
        open(0)
        runCurrent()
        switchMasking(true)
        runCurrent()

        val detail = openDetail()
        assertThat(detail.host).doesNotContain("192.168.1.20")
        assertThat(detail.detail).doesNotContain("10.0.0.7")
    }

    @Test
    fun `switching masking off shows the addresses again`() = runTest {
        trail.entries.value = listOf(entry(host = "Desk PC (192.168.1.20)", detail = null))
        switchMasking(true)
        subscribe()
        runCurrent()
        assertThat(summary()).doesNotContain("192.168.1.20")

        switchMasking(false)
        runCurrent()

        assertThat(summary()).isEqualTo("Desk PC (192.168.1.20)")
    }

    @Test
    fun `a secret in the detail is hidden whatever the masking setting`() = runTest {
        trail.entries.value = listOf(entry(host = "Desk PC", detail = "pin=481516"))
        subscribe()
        runCurrent()

        val summary = summary()
        assertThat(summary).doesNotContain("481516")
    }

    @Test
    fun `no entry is open at first`() = runTest {
        trail.entries.value = listOf(entry())
        subscribe()
        runCurrent()

        assertThat(viewModel.uiState.value.detail).isNull()
    }

    @Test
    fun `selecting an entry opens it in full`() = runTest {
        trail.entries.value =
            listOf(
                entry(AuditType.SESSION_END, "2026-10-16T20:42:00Z"),
                entry(
                    AuditType.PAIRING_FAILURE,
                    "2026-10-12T18:02:09Z",
                    "Studio",
                    "wrong PIN",
                    AuditOutcome.FAILURE,
                ),
            )
        subscribe()
        runCurrent()

        open(1)
        runCurrent()

        assertThat(viewModel.uiState.value.detail)
            .isEqualTo(
                AuditDetail(
                    AuditType.PAIRING_FAILURE,
                    "12 Oct 2026 18:02:09",
                    AuditOutcome.FAILURE,
                    "Studio",
                    "wrong PIN",
                )
            )
    }

    @Test
    fun `dismissing closes the opened entry`() = runTest {
        trail.entries.value = listOf(entry())
        subscribe()
        runCurrent()
        open(0)
        runCurrent()

        viewModel.onDetailDismissed()
        runCurrent()

        assertThat(viewModel.uiState.value.detail).isNull()
    }

    @Test
    fun `a key that is not in the list opens nothing`() = runTest {
        trail.entries.value = listOf(entry())
        subscribe()
        runCurrent()

        viewModel.onEntrySelected("5")
        viewModel.onEntrySelected("")
        viewModel.onEntrySelected("no-such-entry")
        runCurrent()

        assertThat(viewModel.uiState.value.detail).isNull()
    }

    @Test
    fun `the opened entry stays open when newer entries arrive`() = runTest {
        val old = entry(AuditType.SESSION_END, "2026-10-16T20:42:00Z")
        trail.entries.value = listOf(old)
        subscribe()
        runCurrent()
        open(0)
        runCurrent()

        trail.entries.value = listOf(entry(AuditType.LAUNCH_ATTEMPT, "2026-10-16T20:49:00Z"), old)
        runCurrent()

        assertThat(viewModel.uiState.value.detail?.type).isEqualTo(AuditType.SESSION_END)
    }

    @Test
    fun `the opened entry closes when it leaves the trail`() = runTest {
        trail.entries.value = listOf(entry())
        subscribe()
        runCurrent()
        open(0)
        runCurrent()

        trail.entries.value = emptyList()
        runCurrent()

        assertThat(viewModel.uiState.value.detail).isNull()
        assertThat(viewModel.uiState.value.entries).isEmpty()
    }

    @Test
    fun `an entry keeps its key when a newer entry arrives`() = runTest {
        val old = entry(AuditType.SESSION_END, "2026-10-16T20:42:00Z")
        trail.entries.value = listOf(old)
        subscribe()
        runCurrent()
        val before = rows().single().key

        trail.entries.value = listOf(entry(AuditType.LAUNCH_ATTEMPT, "2026-10-16T20:49:00Z"), old)
        runCurrent()

        assertThat(rows()[1].key).isEqualTo(before)
        assertThat(rows()[0].key).isNotEqualTo(before)
    }

    @Test
    fun `the key of an entry does not change with masking`() = runTest {
        trail.entries.value = listOf(entry(host = "Desk PC (192.168.1.20)", detail = "to 10.0.0.7"))
        subscribe()
        runCurrent()
        val before = rows().single().key

        switchMasking(true)
        runCurrent()

        assertThat(rows().single().key).isEqualTo(before)
    }

    @Test
    fun `entries that look the same have different keys`() = runTest {
        trail.entries.value = listOf(entry(), entry(), entry(AuditType.PAIRING_START))
        subscribe()
        runCurrent()

        assertThat(rows().map { it.key }).containsNoDuplicates()
    }

    @Test
    fun `a key read before a newer entry arrived still opens the same entry`() = runTest {
        val old = entry(AuditType.SESSION_END, "2026-10-16T20:42:00Z")
        trail.entries.value = listOf(old)
        subscribe()
        runCurrent()
        val key = rows().single().key
        trail.entries.value = listOf(entry(AuditType.LAUNCH_ATTEMPT, "2026-10-16T20:49:00Z"), old)
        runCurrent()

        viewModel.onEntrySelected(key)
        runCurrent()

        assertThat(viewModel.uiState.value.detail?.type).isEqualTo(AuditType.SESSION_END)
    }

    @Test
    fun `the entries are mapped on the dispatcher it is given, not where they are collected`() =
        runTest {
            val mapping = StandardTestDispatcher(TestCoroutineScheduler())
            val viewModel = AuditViewModel(trail, settings, fixedFormatter(), mapping)
            trail.entries.value = listOf(entry())
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }
            runCurrent()

            assertThat(viewModel.uiState.value.loading).isTrue()

            mapping.scheduler.runCurrent()
            runCurrent()

            assertThat(viewModel.uiState.value.entries).hasSize(1)
        }
}
