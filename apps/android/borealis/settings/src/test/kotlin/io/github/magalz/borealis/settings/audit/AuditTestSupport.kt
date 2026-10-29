package io.github.magalz.borealis.settings.audit

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.diagnostics.audit.AuditEntry
import io.github.magalz.borealis.diagnostics.audit.AuditTrail
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import io.github.magalz.borealis.settings.testing.InGlassHost
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

const val PHONE = "w360dp-h640dp-xhdpi"
const val TV = "w960dp-h540dp-land-television-xhdpi"

/** 2026-10-16 20:50 UTC, the moment of every test. */
val NOW: Instant = Instant.parse("2026-10-16T20:50:00Z")

/** A formatter in UTC and US English, at [NOW]: nothing depends on the machine that runs it. */
fun fixedFormatter() = AuditTimeFormatter({ Clock.fixed(NOW, ZoneOffset.UTC) }, Locale.US)

fun at(text: String): Long = Instant.parse(text).toEpochMilli()

fun entry(
    type: AuditType = AuditType.SESSION_END,
    time: String = "2026-10-16T20:42:00Z",
    host: String? = "Desk PC",
    detail: String? = "Desktop",
    outcome: AuditOutcome = AuditOutcome.SUCCESS,
) = AuditEntry(at(time), type, outcome, host, detail)

/** An [AuditTrail] that holds its entries in memory; [entries] is what [observe] emits. */
class FakeAuditTrail(initial: List<AuditEntry> = emptyList()) : AuditTrail {
    val entries = MutableStateFlow(initial)
    var erased = 0
        private set

    override fun observe(): Flow<List<AuditEntry>> = entries

    override suspend fun erase(): Boolean {
        erased++
        entries.value = emptyList()
        return true
    }
}

/**
 * Shows the Audit trail of [formFactor] with [state], over a glass host of [style] under [look],
 * and does nothing when an entry is chosen or Back is pressed.
 */
fun ComposeContentTestRule.showAudit(
    state: AuditUiState,
    formFactor: FormFactor = FormFactor.Phone,
    look: BorealisLook = BorealisLook(),
    style: BackgroundStyle = BackgroundStyle.FlowingLight,
) {
    setContent {
        InGlassHost(formFactor, look, style) {
            AuditScreen(
                state = state,
                formFactor = formFactor,
                onEntrySelected = {},
                onDetailDismissed = {},
                onBack = {},
            )
        }
    }
    waitForIdle()
}

/** Every event type with the title of the Phase 2 proposal, for the tests that look at all. */
val TITLES: Map<AuditType, String> =
    mapOf(
        AuditType.PAIRING_START to "Pairing started",
        AuditType.PAIRING_SUCCESS to "Paired",
        AuditType.PAIRING_FAILURE to "Pairing failed",
        AuditType.PAIRING_RESTART to "Pairing restarted",
        AuditType.UNPAIR_CONFIRMED to "Unpaired",
        AuditType.UNPAIR_NOT_CONFIRMED to "Unpair not confirmed by the host",
        AuditType.CERTIFICATE_CHANGED to "Host certificate changed",
        AuditType.TLS_FAILURE to "Secure connection failed",
        AuditType.LAUNCH_ATTEMPT to "App started",
        AuditType.LAUNCH_RETRY to "Host asked to retry",
        AuditType.LAUNCH_CONFLICT to "An app was already running on the PC",
        AuditType.SESSION_START to "Stream started",
        AuditType.SESSION_END to "Stream ended",
    )
