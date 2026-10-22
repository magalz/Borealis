package io.github.magalz.borealis.settings.audit

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.assertNoClippedText
import io.github.magalz.borealis.designsystem.testing.textLayout
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FontChoice
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.TextSize
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Audit trail at a system font scale of 2.0, the largest that Android offers (WCAG 1.4.4: text
 * resizes to 200 percent without loss of content). Every text must fit where it is, or the list
 * scrolls: none runs past its width, is cut off at the bottom or ends in an ellipsis.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class AuditLargeTextFitTest(font: FontChoice) {
    @get:Rule val composeRule = createComposeRule()

    private val look = BorealisLook(font = font)

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

    /**
     * A narrow window (split screen) at the largest text: the title of the screen takes the lines
     * it needs and is not shortened. The summaries of the entries are shortened on purpose, so this
     * reads the title only.
     */
    @Test
    @Config(qualifiers = NARROW_WINDOW, fontScale = SCALE)
    fun `the title wraps in a narrow window and does not end in an ellipsis`() {
        composeRule.showAudit(
            entries,
            FormFactor.Phone,
            look.copy(textSize = TextSize.Largest),
            BackgroundStyle.Off,
        )

        val title = composeRule.onNodeWithText("Audit trail", useUnmergedTree = true).textLayout()
        assertThat(title.lineCount).isGreaterThan(1)
        assertThat((0 until title.lineCount).none(title::isLineEllipsized)).isTrue()
    }

    private fun assertFits(formFactor: FormFactor, state: AuditUiState) {
        composeRule.showAudit(state, formFactor, look, BackgroundStyle.Off)

        composeRule.assertNoClippedText()
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun fonts(): List<FontChoice> = FontChoice.entries

        private const val SCALE = 2f
        private const val NARROW_WINDOW = "w240dp-h568dp-xhdpi"
    }
}
