package io.github.magalz.borealis.stream.ui.keyboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.assertNoClippedText
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.TextSize
import io.github.magalz.borealis.designsystem.theme.TextSpacing
import io.github.magalz.borealis.stream.testing.RecordingIntents
import io.github.magalz.borealis.stream.testing.TV
import io.github.magalz.borealis.stream.ui.StreamKeyboard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The row of shortcuts of the keyboard and the chips after it (System keyboard and Close): beside
 * the shortcuts, which scroll, while the chips leave them a quarter of the row or more; under them
 * when they leave less, which the text at the largest size with the letters spaced out does on a
 * narrow phone, so that the shortcuts keep a width in which they can be seen and scrolled, and no
 * chip is squeezed to a sliver in which its caption breaks letter by letter.
 */
@RunWith(AndroidJUnit4::class)
class ShortcutRowTest {
    @get:Rule val composeRule = createComposeRule()

    private fun show(formFactor: FormFactor, look: BorealisLook) {
        composeRule.setContent {
            BorealisTheme(formFactor, look) {
                Box(Modifier.fillMaxSize()) { StreamKeyboard(true, RecordingIntents()) }
            }
        }
        composeRule.waitForIdle()
    }

    private fun top(tag: String) = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot().top

    private fun bottom(tag: String) =
        composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot().bottom

    @Test
    @Config(qualifiers = NARROW_PHONE, fontScale = 1.3f)
    fun `the chips go under the shortcuts when they would leave them less than a quarter of the row`() {
        show(
            FormFactor.Phone,
            BorealisLook(textSize = TextSize.Largest, textSpacing = TextSpacing.Wider),
        )

        assertThat(top("chip:Close")).isAtLeast(bottom("chip:Esc"))
        assertThat(top("chip:System keyboard")).isAtLeast(bottom("chip:Esc"))
        composeRule.assertNoClippedText()
    }

    @Test
    @Config(qualifiers = TV)
    fun `the chips are beside the shortcuts when they leave them a quarter of the row or more`() {
        show(FormFactor.Tv, BorealisLook(textSpacing = TextSpacing.Wider))

        assertThat(top("chip:Close")).isLessThan(bottom("chip:Esc"))
        assertThat(top("chip:System keyboard")).isLessThan(bottom("chip:Esc"))
    }

    @Test
    @Config(qualifiers = NARROW_PHONE)
    fun `at Normal the chips are beside the shortcuts on a narrow phone`() {
        show(FormFactor.Phone, BorealisLook())

        assertThat(top("chip:Close")).isLessThan(bottom("chip:Esc"))
    }

    private companion object {
        const val NARROW_PHONE = "w320dp-h568dp-xhdpi"
    }
}
