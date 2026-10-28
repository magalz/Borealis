package io.github.magalz.borealis.designsystem.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.rememberTextMeasurer
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
    fun `text and icons default to the ink colour under the theme`() {
        val seen = themed(formFactor = null)

        assertThat(seen.contentColor).isEqualTo(BorealisTokens.Color.Ink)
    }

    @Test
    fun `PIN digits are monospaced and Sans is not`() {
        var narrowPin = 0
        var widePin = 0
        var narrowSans = 0
        var wideSans = 0
        composeRule.setContent {
            BorealisTheme {
                val measurer = rememberTextMeasurer()
                val pin = LocalBorealisType.current.pin
                val sans = MaterialTheme.typography.bodyMedium
                narrowPin = measurer.measure("1111", pin).size.width
                widePin = measurer.measure("8888", pin).size.width
                narrowSans = measurer.measure("iiii", sans).size.width
                wideSans = measurer.measure("WWWW", sans).size.width
            }
        }
        composeRule.waitForIdle()

        assertThat(narrowPin).isEqualTo(widePin)
        assertThat(narrowSans).isLessThan(wideSans)
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
            contentColor = LocalContentColor.current,
        )

    /** What the theme gave the content: a value holder, so a data class. */
    private data class Seen(
        val formFactor: FormFactor,
        val titleSize: TextUnit,
        val background: Color,
        val accent: Color,
        val gutterDp: Float,
        val pinSize: TextUnit,
        val contentColor: Color,
    )
}
