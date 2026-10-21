package io.github.magalz.borealis.settings.audit

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import io.github.magalz.borealis.settings.testing.FlowingLightGlassCases
import io.github.magalz.borealis.settings.testing.Probe
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The list of the Audit trail is on one glass panel. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class AuditListGlassTest : FlowingLightGlassCases() {
    // In the padding of the panel, which is at 8dp, 2dp in from its edge, beside the first entry;
    // the background is read 2dp outside the panel.
    override val probes = listOf(Probe("Desk PC · Desktop", 10f, outside = 6f))

    override fun showScreen(look: BorealisLook, style: BackgroundStyle) =
        composeRule.showAudit(
            AuditUiState(
                loading = false,
                entries =
                    listOf(
                        AuditRow("end", AuditType.SESSION_END, "20:42", "Desk PC · Desktop"),
                        AuditRow("start", AuditType.SESSION_START, "20:34", "Desk PC · HEVC"),
                    ),
            ),
            look = look,
            style = style,
        )
}

/** The opened entry of the Audit trail is a glass card. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class AuditDetailGlassTest : FlowingLightGlassCases() {
    // In the padding of the card, which is at the 16dp gutter, 2dp in from its edge, beside the
    // time; the background is read 2dp outside the card. The rows below it are over a steeper edge
    // of the still, where the blur and the 4dp between the two points make a larger difference.
    override val probes = listOf(Probe("12 Oct 2026 18:02:09", 18f, outside = 14f))

    override fun showScreen(look: BorealisLook, style: BackgroundStyle) =
        composeRule.showAudit(
            AuditUiState(
                loading = false,
                detail =
                    AuditDetail(
                        AuditType.PAIRING_FAILURE,
                        "12 Oct 2026 18:02:09",
                        AuditOutcome.FAILURE,
                        "Studio (192.168.1.40)",
                        "Host answered 401 after the PIN",
                    ),
            ),
            look = look,
            style = style,
        )
}
