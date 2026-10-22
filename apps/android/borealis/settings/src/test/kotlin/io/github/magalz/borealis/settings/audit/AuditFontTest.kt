package io.github.magalz.borealis.settings.audit

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.fontFileNames
import io.github.magalz.borealis.designsystem.testing.textLayout
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FontChoice
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisLook
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Audit trail draws its text in the font of the look, and its codes (the time of a row, the
 * name of an event) in the monospaced companion of that font (ADR 0058, decision 2).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class AuditFontTest {
    @get:Rule val composeRule = createComposeRule()

    private val row = AuditRow("end", AuditType.SESSION_END, "20:42", "Desk PC · Desktop")
    private val detail =
        AuditDetail(
            AuditType.PAIRING_FAILURE,
            "12 Oct 2026 18:02:09",
            AuditOutcome.FAILURE,
            "Studio (192.168.1.40)",
            "Host answered 401 after the PIN",
        )

    private fun show(font: FontChoice, state: AuditUiState) {
        composeRule.setContent {
            CompositionLocalProvider(LocalBorealisLook provides BorealisLook(font = font)) {
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

    private fun files(text: String): List<String> =
        composeRule
            .onNodeWithText(text, useUnmergedTree = true)
            .textLayout()
            .layoutInput
            .style
            .fontFamily
            .fontFileNames(ApplicationProvider.getApplicationContext<Context>().resources)

    @Test
    fun `with B612 the time and the event name of a row draw in B612 Mono`() {
        show(FontChoice.B612, AuditUiState(loading = false, entries = listOf(row), detail = null))

        assertThat(files("20:42")).containsExactly("b612_mono_regular", "b612_mono_bold").inOrder()
        assertThat(files(AuditType.SESSION_END.name))
            .containsExactly("b612_mono_regular", "b612_mono_bold")
            .inOrder()
    }

    @Test
    fun `with B612 the summary of a row draws in B612`() {
        show(FontChoice.B612, AuditUiState(loading = false, entries = listOf(row), detail = null))

        assertThat(files("Desk PC · Desktop"))
            .containsExactly("b612_regular", "b612_bold")
            .inOrder()
    }

    @Test
    fun `with B612 the event of the detail draws in B612 Mono and its other fields in B612`() {
        show(FontChoice.B612, AuditUiState(loading = false, entries = listOf(row), detail = detail))

        assertThat(files(AuditType.PAIRING_FAILURE.name))
            .containsExactly("b612_mono_regular", "b612_mono_bold")
            .inOrder()
        assertThat(files("Studio (192.168.1.40)"))
            .containsExactly("b612_regular", "b612_bold")
            .inOrder()
    }
}
