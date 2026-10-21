package io.github.magalz.borealis.settings.audit

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.NoTouchOnTv
import io.github.magalz.borealis.designsystem.testing.assertAccessible
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import io.github.magalz.borealis.settings.testing.InGlassHost
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Google's Accessibility Test Framework over the Audit trail screen: the list, the empty trail and
 * the details of an entry. The glass is off here (`BackgroundStyle.Off`), so the contrast checks
 * see the colours that the text really has.
 */
@RunWith(AndroidJUnit4::class)
class AuditAccessibilityChecksTest {
    @get:Rule val composeRule = createComposeRule()

    private val entries =
        AuditUiState(
            loading = false,
            entries =
                listOf(
                    AuditRow("end", AuditType.SESSION_END, "20:42", "Desk PC · Desktop"),
                    AuditRow("start", AuditType.SESSION_START, "20:34", "Desk PC · HEVC"),
                    AuditRow("cert", AuditType.CERTIFICATE_CHANGED, "Mon 17:00", "Living Room PC"),
                ),
        )
    private val detail =
        AuditDetail(
            AuditType.PAIRING_FAILURE,
            "12 Oct 2026 18:02:09",
            AuditOutcome.FAILURE,
            "Studio (192.168.1.40)",
            "Host answered 401 after the PIN",
        )

    @Test
    @Config(qualifiers = PHONE)
    fun `the trail on a phone`() {
        show(FormFactor.Phone, entries)

        composeRule.assertAccessible()
    }

    @Test
    @Config(qualifiers = TV)
    fun `the trail on a TV`() {
        show(FormFactor.Tv, entries)

        composeRule.assertAccessible(NoTouchOnTv)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `an empty trail`() {
        show(FormFactor.Phone, AuditUiState(loading = false))

        composeRule.assertAccessible()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the details of an entry`() {
        show(FormFactor.Phone, entries.copy(detail = detail))

        composeRule.assertAccessible()
    }

    private fun show(formFactor: FormFactor, state: AuditUiState) {
        composeRule.setContent {
            InGlassHost(formFactor, style = BackgroundStyle.Off) {
                AuditScreen(
                    state,
                    formFactor,
                    onEntrySelected = {},
                    onDetailDismissed = {},
                    onBack = {},
                )
            }
        }
        composeRule.waitForIdle()
    }
}
