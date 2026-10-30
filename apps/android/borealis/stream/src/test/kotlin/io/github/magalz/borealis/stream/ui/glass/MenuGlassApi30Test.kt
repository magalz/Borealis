package io.github.magalz.borealis.stream.ui.glass

import android.os.Build
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.stream.testing.PHONE_LANDSCAPE
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Below API 31 nothing blurs, and the stream glass is solid with no copy (ADR 0061, decision 3):
 * the owner allows only Blur or Solid, with no tint-only glass as the Library has on API 28 to 30.
 * `VideoGlassSpecTest` has the rule; this is that the menu asks the device it runs on.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.R], qualifiers = PHONE_LANDSCAPE)
class MenuGlassApi30Test {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `on API 30 the menu is the solid surface and no copy is asked for`() {
        val glass =
            GlassFixture(
                composeRule,
                FakeVideoSource(frameColor = android.graphics.Color.RED, completing = true),
            )
        glass.show()

        glass.run(1_000)

        assertThat(glass.source!!.requests).isEmpty()
        assertThat(powerSaveReceivers()).isEmpty()
        assertThat(glass.panel().leftPixel().red).isWithin(0.012f).of(BorealisColors().surface.red)
    }
}
