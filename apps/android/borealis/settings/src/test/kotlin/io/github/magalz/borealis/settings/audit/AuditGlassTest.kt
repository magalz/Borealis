package io.github.magalz.borealis.settings.audit

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import io.github.magalz.borealis.settings.testing.FlowingLightGlassCases
import io.github.magalz.borealis.settings.testing.InGlassHost
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private fun FlowingLightGlassCases.showAudit(
    state: AuditUiState,
    look: BorealisLook,
    style: BackgroundStyle,
) {
    composeRule.setContent {
        InGlassHost(FormFactor.Phone, look, style) {
            AuditScreen(
                state = state,
                formFactor = FormFactor.Phone,
                onEntrySelected = {},
                onDetailDismissed = {},
                onBack = {},
            )
        }
    }
    composeRule.waitForIdle()
}

/** The list of the Audit trail is on one glass panel. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class AuditListGlassTest : FlowingLightGlassCases() {
    // In the padding of the panel, 4dp from its edge, beside the first entry.
    override val probes = listOf("Desk PC · Desktop" to 12f)

    override fun showScreen(look: BorealisLook, style: BackgroundStyle) =
        showAudit(
            AuditUiState(
                loading = false,
                entries =
                    listOf(
                        AuditRow("end", AuditType.SESSION_END, "20:42", "Desk PC · Desktop"),
                        AuditRow("start", AuditType.SESSION_START, "20:34", "Desk PC · HEVC"),
                    ),
            ),
            look,
            style,
        )
}

/** The opened entry of the Audit trail is a glass card. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class AuditDetailGlassTest : FlowingLightGlassCases() {
    // In the padding of the card, which is at the gutter, beside the host.
    override val probes = listOf("Studio (192.168.1.40)" to 22f)

    override fun showScreen(look: BorealisLook, style: BackgroundStyle) =
        showAudit(
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
            look,
            style,
        )
}
