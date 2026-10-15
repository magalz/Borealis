package io.github.magalz.borealis.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BorealisThemeCompositionTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `the theme provides the phone defaults`() {
        val seen = themed(formFactor = null)

        assertThat(seen.formFactor).isEqualTo(FormFactor.Phone)
        assertThat(seen.titleSize).isEqualTo(18.sp)
        assertThat(seen.background).isEqualTo(BorealisTokens.Color.Page)
        assertThat(seen.accent).isEqualTo(BorealisTokens.Color.Accent)
        assertThat(seen.gutterDp).isEqualTo(16f)
    }

    @Test
    fun `the TV theme switches the form factor type and spacing together`() {
        val seen = themed(FormFactor.Tv)

        assertThat(seen.formFactor).isEqualTo(FormFactor.Tv)
        assertThat(seen.titleSize).isEqualTo(24.sp)
        assertThat(seen.gutterDp).isEqualTo(34f)
        assertThat(seen.pinSize).isEqualTo(34.sp)
    }

    private fun themed(formFactor: FormFactor?): Seen {
        var seen: Seen? = null
        composeRule.setContent {
            if (formFactor == null) {
                BorealisTheme { seen = readSeen() }
            } else {
                BorealisTheme(formFactor) { seen = readSeen() }
            }
        }
        composeRule.waitForIdle()
        return checkNotNull(seen) { "the themed content did not compose" }
    }

    @Composable
    private fun readSeen() =
        Seen(
            formFactor = LocalFormFactor.current,
            titleSize = MaterialTheme.typography.titleLarge.fontSize,
            background = MaterialTheme.colorScheme.background,
            accent = LocalBorealisColors.current.accent,
            gutterDp = LocalBorealisSpacing.current.gutter.value,
            pinSize = LocalBorealisType.current.pin.fontSize,
        )

    private class Seen(
        val formFactor: FormFactor,
        val titleSize: TextUnit,
        val background: Color,
        val accent: Color,
        val gutterDp: Float,
        val pinSize: TextUnit,
    )
}
