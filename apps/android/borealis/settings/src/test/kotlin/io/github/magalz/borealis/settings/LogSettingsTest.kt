package io.github.magalz.borealis.settings

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.logging.AuditEvent
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditSink
import io.github.magalz.borealis.logging.AuditType
import io.github.magalz.borealis.logging.BorealisAudit
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.logging.LogRecord
import io.github.magalz.borealis.logging.LogSink
import io.github.magalz.borealis.logging.LogTag
import io.github.magalz.borealis.logging.Redactor
import io.github.magalz.borealis.settings.catalog.BooleanSetting
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.repository.SettingsSnapshot
import io.github.magalz.borealis.settings.testing.FakeSettingsRepository
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Test

/** The logging switches of the repository are applied to the logging, now and on each change. */
class LogSettingsTest {
    private val messages = CopyOnWriteArrayList<String>()
    private val auditDetails = CopyOnWriteArrayList<String?>()
    private val sink =
        object : LogSink {
            override fun write(record: LogRecord) {
                messages += record.message
            }
        }
    private var logSettings: LogSettings? = null

    @After
    fun tearDown() {
        logSettings?.stop()
        BorealisLog.shutdown()
        BorealisAudit.uninstall()
    }

    private fun startLogging(
        baseLevel: LogLevel,
        repository: FakeSettingsRepository = FakeSettingsRepository(),
    ): FakeSettingsRepository {
        val redactor = Redactor()
        BorealisLog.initialize(listOf(sink), minLevel = baseLevel, redactor = redactor)
        BorealisAudit.install(
            AuditSink { event: AuditEvent -> auditDetails += event.detail },
            redactor,
            "1.0",
        )
        logSettings = LogSettings.start(repository, baseLevel)
        return repository
    }

    private fun primed(vararg switches: Pair<BooleanSetting, Boolean>) =
        FakeSettingsRepository(
            switches.fold(SettingsSnapshot.DEFAULTS) { acc, (setting, value) ->
                acc.with(setting, value)
            }
        )

    private fun logDebugAndAddress() {
        BorealisLog.d(LogTag.APP, "debug line")
        BorealisLog.i(LogTag.APP, "to 10.0.0.5")
    }

    @Test
    fun `by default a release build logs info and above with addresses visible`() {
        startLogging(LogLevel.INFO)

        logDebugAndAddress()

        assertThat(messages).containsExactly("to 10.0.0.5")
    }

    @Test
    fun `a stored verbose switch lowers the level to debug at startup`() {
        startLogging(LogLevel.INFO, primed(Settings.VERBOSE_LOGGING to true))

        logDebugAndAddress()

        assertThat(messages).containsExactly("debug line", "to 10.0.0.5").inOrder()
    }

    @Test
    fun `a stored masking switch masks addresses at startup`() {
        startLogging(LogLevel.INFO, primed(Settings.MASK_ADDRESSES to true))

        logDebugAndAddress()

        assertThat(messages).containsExactly("to [IP]")
    }

    @Test
    fun `the verbose switch takes effect while the app runs and can be turned off again`() {
        val repository = startLogging(LogLevel.INFO)

        repository.setNow(Settings.VERBOSE_LOGGING, true)
        BorealisLog.d(LogTag.APP, "while on")
        repository.setNow(Settings.VERBOSE_LOGGING, false)
        BorealisLog.d(LogTag.APP, "while off")

        assertThat(messages).containsExactly("while on")
    }

    @Test
    fun `the masking switch takes effect at once in the log and the audit trail, and can be turned off again`() {
        val repository = startLogging(LogLevel.INFO)

        repository.setNow(Settings.MASK_ADDRESSES, true)
        BorealisLog.i(LogTag.APP, "on 10.0.0.5")
        BorealisAudit.record(AuditType.PAIRING_START, AuditOutcome.SUCCESS, detail = "on 10.0.0.5")
        repository.setNow(Settings.MASK_ADDRESSES, false)
        BorealisLog.i(LogTag.APP, "off 10.0.0.5")
        BorealisAudit.record(AuditType.PAIRING_START, AuditOutcome.SUCCESS, detail = "off 10.0.0.5")

        assertThat(messages).containsExactly("on [IP]", "off 10.0.0.5").inOrder()
        assertThat(auditDetails).containsExactly("on [IP]", "off 10.0.0.5").inOrder()
    }

    @Test
    fun `a debug build keeps debug when the verbose switch is off`() {
        val repository = startLogging(LogLevel.DEBUG)

        BorealisLog.d(LogTag.APP, "debug line")
        repository.setNow(Settings.VERBOSE_LOGGING, true)
        repository.setNow(Settings.VERBOSE_LOGGING, false)
        BorealisLog.d(LogTag.APP, "still debug")

        assertThat(messages).containsExactly("debug line", "still debug").inOrder()
    }

    @Test
    fun `a change of another setting leaves the logging as it is`() {
        val repository = startLogging(LogLevel.INFO, primed(Settings.MASK_ADDRESSES to true))

        repository.setNow(Settings.FRAME_RATE, "120")
        logDebugAndAddress()

        assertThat(messages).containsExactly("to [IP]")
    }

    @Test
    fun `after stop the switches are no longer followed`() {
        val repository = startLogging(LogLevel.INFO)

        logSettings?.stop()
        repository.setNow(Settings.MASK_ADDRESSES, true)
        BorealisLog.i(LogTag.APP, "to 10.0.0.5")

        assertThat(messages).containsExactly("to 10.0.0.5")
    }
}
