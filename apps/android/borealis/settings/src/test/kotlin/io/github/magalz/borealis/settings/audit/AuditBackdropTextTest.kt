package io.github.magalz.borealis.settings.audit

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.magalz.borealis.designsystem.components.BackdropTextHalo
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.logging.AuditType
import io.github.magalz.borealis.settings.testing.InGlassHost
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The title of the Audit trail with how long it keeps the entries, and its empty state, stand on
 * the background and have the halo, on a phone and on a TV. The list is a glass panel and has none.
 */
@RunWith(AndroidJUnit4::class)
class AuditBackdropTextTest {
    @get:Rule val composeRule = createComposeRule()

    private val entries =
        AuditUiState(
            loading = false,
            entries = listOf(AuditRow("end", AuditType.SESSION_END, "20:42", "Desk PC · Desktop")),
        )
    private val empty = AuditUiState(loading = false)

    @Test
    @Config(qualifiers = PHONE)
    fun `the title and the retention on a phone are on the background`() {
        show(entries, FormFactor.Phone)

        assertHalo("Audit trail")
        assertHalo("Kept for 90 days or 2,000 entries")
    }

    @Test
    @Config(qualifiers = TV)
    fun `the title and the retention on a TV are on the background`() {
        show(entries, FormFactor.Tv)

        assertHalo("Audit trail")
        assertHalo("Kept for 90 days or 2,000 entries")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the empty state is on the background`() {
        show(empty, FormFactor.Phone)

        assertHalo("No events recorded yet.")
        assertHalo("Pairing, launch and session events appear here.")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `an entry of the list is on glass and has no halo`() {
        show(entries, FormFactor.Phone)

        composeRule
            .onNode(
                hasText("Desk PC · Desktop") and SemanticsMatcher.keyNotDefined(BackdropTextHalo),
                useUnmergedTree = true,
            )
            .assertExists()
    }

    private fun show(state: AuditUiState, formFactor: FormFactor) {
        composeRule.setContent {
            InGlassHost(formFactor) {
                AuditScreen(
                    state = state,
                    formFactor = formFactor,
                    onEntrySelected = {},
                    onDetailDismissed = {},
                    onBack = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun assertHalo(text: String) {
        composeRule
            .onNode(
                hasText(text) and SemanticsMatcher.expectValue(BackdropTextHalo, true),
                useUnmergedTree = true,
            )
            .assertExists()
    }
}
