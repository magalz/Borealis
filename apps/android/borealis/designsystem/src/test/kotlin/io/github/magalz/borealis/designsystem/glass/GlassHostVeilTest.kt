package io.github.magalz.borealis.designsystem.glass

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.noAnimations
import io.github.magalz.borealis.designsystem.theme.BackgroundIntensity
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The veil of the Background intensity lies under the glass: the host blurs the background with the
 * veil in it, so a glass surface is its tint over the background that the veil left. Every step of
 * that, the veil, the blur and the tint, is a mix of colours, so the surface at a lower intensity
 * follows from the surface at 100 percent.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class GlassHostVeilTest {
    @get:Rule val composeRule = createComposeRule()

    private val style = BackgroundStyle.FlowingLight
    private val page = BorealisTokens.Color.Page
    private val surface = BorealisTokens.Color.Surface

    private var look by mutableStateOf(BorealisLook())

    @Test
    fun `a glass surface shows the background that the veil left`() {
        noAnimations()
        composeRule.setContent {
            BorealisTheme(FormFactor.Phone, look) {
                GlassHost(style) {
                    Box(Modifier.size(100.dp).testTag(PANEL).glassSurface(RoundedCornerShape(8.dp)))
                }
            }
        }
        composeRule.waitForIdle()
        val tint = glassSpec(look, style, Build.VERSION.SDK_INT).tint
        val full = panelCentre()
        // The bright background shows through the surface, which is far from the surface colour.
        assertThat(full[0]).isGreaterThan(surface.red + SHOWS_THROUGH)
        // The background behind the surface at full strength, with the tint taken off.
        val behind = full.mapIndexed { i, c -> (c - tint * surface.channel(i)) / (1f - tint) }

        BackgroundIntensity.entries.forEach { level ->
            look = BorealisLook(backgroundIntensity = level)
            composeRule.waitForIdle()

            val veiled = behind.mapIndexed { i, c ->
                c * (1f - level.veil) + page.channel(i) * level.veil
            }
            val expected = veiled.mapIndexed { i, c -> tint * surface.channel(i) + (1f - tint) * c }
            val actual = panelCentre()
            // Sum of the channels: the veil of 0.25 moves the surface by about 0.07 of it.
            val apart = expected.indices.sumOf { abs(actual[it] - expected[it]).toDouble() }
            assertWithMessage("$level: $actual, expected $expected")
                .that(apart)
                .isLessThan(TOLERANCE)
        }
    }

    private fun panelCentre(): List<Float> {
        val shot = composeRule.onNodeWithTag(PANEL).captureToImage().toPixelMap()
        val colour = shot[shot.width / 2, shot.height / 2]
        return listOf(colour.red, colour.green, colour.blue)
    }

    private fun Color.channel(i: Int) = listOf(red, green, blue)[i]

    private companion object {
        const val PANEL = "panel"
        const val TOLERANCE = 0.02
        const val SHOWS_THROUGH = 0.05f
    }
}
