package io.github.magalz.borealis.settings

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.logging.AuditEvent
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditSink
import io.github.magalz.borealis.logging.AuditType
import io.github.magalz.borealis.logging.BorealisAudit
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.logging.Redactor
import io.github.magalz.borealis.settings.catalog.SettingStore
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.repository.SettingsSnapshot
import io.github.magalz.borealis.settings.store.StoreSettingsRepository
import io.github.magalz.borealis.settings.testing.FakeBacking
import io.github.magalz.borealis.settings.testing.WorkerDispatcherRule
import io.github.magalz.borealis.settings.testing.eventually
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The audit trail viewer masks its rows with the global redactor, and masks them again when
 * `observe(MASK_ADDRESSES)` emits. So the redactor must be switched before the repository emits the
 * new value, not after.
 */
class LogSettingsPublishOrderTest {
    @get:Rule val workers = WorkerDispatcherRule()

    private val scope = CoroutineScope(workers.dispatcher)
    private val repository =
        StoreSettingsRepository(
            FakeBacking(SettingStore.PREFS),
            FakeBacking(SettingStore.DATASTORE),
            scope,
            SettingsSnapshot.DEFAULTS,
        )
    private val exported = CopyOnWriteArrayList<Pair<Boolean, String?>>()
    private var logSettings: LogSettings? = null

    @Before
    fun startLogging() {
        val redactor = Redactor()
        BorealisLog.initialize(emptyList(), minLevel = LogLevel.INFO, redactor = redactor)
        BorealisAudit.install(AuditSink {}, redactor, "1.0")
        logSettings = LogSettings.start(repository, LogLevel.INFO)
        scope.launch {
            repository.observe(Settings.MASK_ADDRESSES).collect { masked ->
                exported += masked to BorealisAudit.redactForExport(storedEvent()).host
            }
        }
        eventually { exported.size == 1 }
    }

    @After
    fun tearDown() {
        logSettings?.stop()
        scope.cancel()
        BorealisLog.shutdown()
        BorealisAudit.uninstall()
    }

    private fun storedEvent() =
        AuditEvent(0L, AuditType.PAIRING_START, AuditOutcome.SUCCESS, "10.0.0.5", null, "1.0")

    @Test
    fun `after set a collector of the masking switch already finds addresses masked`() =
        runBlocking<Unit> {
            repository.set(Settings.MASK_ADDRESSES, true)

            eventually { exported.size == 2 }
            assertThat(exported.last().first).isTrue()
            assertThat(exported.last().second).doesNotContain("10.0.0.5")
        }

    @Test
    fun `after setNow a collector of the masking switch already finds addresses masked`() {
        repository.setNow(Settings.MASK_ADDRESSES, true)

        eventually { exported.size == 2 }
        assertThat(exported.last().first).isTrue()
        assertThat(exported.last().second).doesNotContain("10.0.0.5")
    }

    @Test
    fun `turning the switch off shows addresses again by the time the collector sees it`() =
        runBlocking<Unit> {
            repository.set(Settings.MASK_ADDRESSES, true)
            eventually { exported.size == 2 }

            repository.set(Settings.MASK_ADDRESSES, false)

            eventually { exported.size == 3 }
            assertThat(exported.last()).isEqualTo(false to "10.0.0.5")
        }

    @Test
    fun `the switches of the primed snapshot are applied when logging starts`() {
        logSettings?.stop()
        val primed =
            StoreSettingsRepository(
                FakeBacking(SettingStore.PREFS),
                FakeBacking(SettingStore.DATASTORE),
                scope,
                SettingsSnapshot.DEFAULTS.with(Settings.MASK_ADDRESSES, true),
            )

        logSettings = LogSettings.start(primed, LogLevel.INFO)

        assertThat(BorealisAudit.redactForExport(storedEvent()).host).doesNotContain("10.0.0.5")
    }
}
