package io.github.magalz.borealis.settings.audit

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.assertNoClippedText
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import io.github.magalz.borealis.settings.testing.InGlassHost
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Audit trail at a system font scale of 2.0, the largest that Android offers (WCAG 1.4.4: text
 * resizes to 200 percent without loss of content). Every text must fit where it is, or the list
 * scrolls: none runs past its width, is cut off at the bottom or ends in an ellipsis.
 */
@RunWith(AndroidJUnit4::class)
class AuditLargeTextFitTest {
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

    @Test
    @Config(qualifiers = PHONE, fontScale = SCALE)
    fun `the trail on a phone fits`() = assertFits(FormFactor.Phone, entries)

    @Test
    @Config(qualifiers = TV, fontScale = SCALE)
    fun `the trail on a TV fits`() = assertFits(FormFactor.Tv, entries)

    @Test
    @Config(qualifiers = PHONE, fontScale = SCALE)
    fun `the details of an entry on a phone fit`() =
        assertFits(
            FormFactor.Phone,
            entries.copy(
                detail =
                    AuditDetail(
                        AuditType.PAIRING_FAILURE,
                        "12 Oct 2026 18:02:09",
                        AuditOutcome.FAILURE,
                        "Studio (192.168.1.40)",
                        "Host answered 401 after the PIN",
                    )
            ),
        )

    private fun assertFits(formFactor: FormFactor, state: AuditUiState) {
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

        composeRule.assertNoClippedText()
    }

    private companion object {
        const val SCALE = 2f
    }
}
