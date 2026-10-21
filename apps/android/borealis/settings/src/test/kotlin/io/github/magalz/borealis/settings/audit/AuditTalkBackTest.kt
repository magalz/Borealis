package io.github.magalz.borealis.settings.audit

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.headings
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.logging.AuditType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What a screen reader is told on the Audit trail (WCAG 2.4.6): the name of the screen is a
 * heading.
 */
@RunWith(AndroidJUnit4::class)
class AuditTalkBackTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = PHONE)
    fun `the title of the trail is a heading on a phone`() {
        show(FormFactor.Phone)

        assertThat(composeRule.headings()).containsExactly("Audit trail")
    }

    @Test
    @Config(qualifiers = TV)
    fun `the title of the trail is a heading on a TV`() {
        show(FormFactor.Tv)

        assertThat(composeRule.headings()).containsExactly("Audit trail")
    }

    private fun show(formFactor: FormFactor) =
        composeRule.showAudit(
            AuditUiState(
                loading = false,
                entries =
                    listOf(AuditRow("end", AuditType.SESSION_END, "20:42", "Desk PC · Desktop")),
            ),
            formFactor,
            style = BackgroundStyle.Off,
        )
}
