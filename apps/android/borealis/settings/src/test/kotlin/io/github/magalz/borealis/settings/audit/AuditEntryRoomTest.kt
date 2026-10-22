package io.github.magalz.borealis.settings.audit

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.fitProblems
import io.github.magalz.borealis.designsystem.testing.textLayout
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FontChoice
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.TextSpacing
import io.github.magalz.borealis.logging.AuditType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The room that an entry of the Audit trail on a phone gives its title. The time is a column of its
 * own beside the title, and "Mon 17:00" with the spacing of Wider took so much of a narrow row that
 * "certificate" ran past the title's width. The time takes a share of the row at most, and wraps at
 * its space (ADR 0058, decision 4).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class AuditEntryRoomTest(private val font: FontChoice) {
    @get:Rule val composeRule = createComposeRule()

    private val state =
        AuditUiState(
            loading = false,
            entries =
                listOf(
                    AuditRow("cert", AuditType.CERTIFICATE_CHANGED, "Mon 17:00", "Living Room PC")
                ),
        )

    private fun assertTitleFits(look: BorealisLook) {
        composeRule.showAudit(state, FormFactor.Phone, look, BackgroundStyle.Off)

        val problems =
            composeRule
                .onNodeWithText("Host certificate changed", useUnmergedTree = true)
                .textLayout()
                .fitProblems()
        assertWithMessage("the title of the entry in $font").that(problems).isEmpty()
    }

    @Test
    @Config(qualifiers = NARROW_WINDOW, fontScale = 1.3f)
    fun `the title fits beside a long time in a narrow row at Wider`() =
        assertTitleFits(BorealisLook(font = font, textSpacing = TextSpacing.Wider))

    @Test
    @Config(qualifiers = NARROW_WINDOW, fontScale = 1.3f)
    fun `the title fits beside a long time in a narrow row at Normal`() =
        assertTitleFits(BorealisLook(font = font))

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun fonts(): List<FontChoice> = FontChoice.entries

        private const val NARROW_WINDOW = "w240dp-h640dp-xhdpi"
    }
}
