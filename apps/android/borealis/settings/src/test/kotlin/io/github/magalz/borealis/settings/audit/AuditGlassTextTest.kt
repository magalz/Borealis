package io.github.magalz.borealis.settings.audit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.noAnimations
import io.github.magalz.borealis.designsystem.testing.textColor
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.Transparency
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The secondary text of the Audit trail is on glass, so it has the glass colours: over Flowing
 * light at 40 percent and 8dp, ink-3 is `#A8ACB1`. Where the glass is solid it has the tokens.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class AuditGlassTextTest {
    @get:Rule val composeRule = createComposeRule()

    private val glassInk3 = Color(0xFFA8ACB1)

    @Before fun theBackgroundIsItsStill() = noAnimations()

    private fun showList(look: BorealisLook) =
        show(AuditUiState(loading = false, entries = ROWS), look)

    private fun showDetail(look: BorealisLook) =
        show(AuditUiState(loading = false, detail = DETAIL), look)

    private fun show(state: AuditUiState, look: BorealisLook) =
        composeRule.showAudit(state, look = look)

    @Test
    fun `the time of an entry of the list is the glass ink-3`() {
        showList(BorealisLook())

        assertThat(composeRule.textColor("20:42")).isEqualTo(glassInk3)
    }

    @Test
    fun `the time of an entry of the list is the token ink-3 where the glass is off`() {
        showList(BorealisLook(transparency = Transparency.Off))

        assertThat(composeRule.textColor("20:42")).isEqualTo(BorealisColors().ink3)
    }

    @Test
    fun `the labels of the opened entry are the glass ink-3`() {
        showDetail(BorealisLook())

        assertThat(composeRule.textColor("Host")).isEqualTo(glassInk3)
    }

    @Test
    fun `the labels of the opened entry are the token ink-3 where the glass is off`() {
        showDetail(BorealisLook(transparency = Transparency.Off))

        assertThat(composeRule.textColor("Host")).isEqualTo(BorealisColors().ink3)
    }

    private companion object {
        val ROWS = listOf(AuditRow("end", AuditType.SESSION_END, "20:42", "Desk PC · Desktop"))
        val DETAIL =
            AuditDetail(
                AuditType.PAIRING_FAILURE,
                "12 Oct 2026 18:02:09",
                AuditOutcome.FAILURE,
                "Studio (192.168.1.40)",
                "Host answered 401 after the PIN",
            )
    }
}
