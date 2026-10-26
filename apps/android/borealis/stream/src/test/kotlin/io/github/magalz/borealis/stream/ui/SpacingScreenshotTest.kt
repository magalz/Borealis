package io.github.magalz.borealis.stream.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.TextSpacing
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.testing.RecordingIntents
import io.github.magalz.borealis.stream.testing.TV
import io.github.magalz.borealis.stream.testing.aPerf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screenshots of the in-game menu on a TV at Wide and Wider, in IBM Plex (ADR 0058, decision 4),
 * over the gradient that stands for the stream. The references live in `src/test/screenshots`,
 * named `menu_tv_spacing-wide.png` and `menu_tv_spacing-wider.png`.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TV)
class SpacingScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun menuTvWide() = menuTv(TextSpacing.Wide, "wide")

    @Test fun menuTvWider() = menuTv(TextSpacing.Wider, "wider")

    private fun menuTv(spacing: TextSpacing, name: String) {
        composeRule.setContentInKeyboardMode {
            BorealisTheme(FormFactor.Tv, BorealisLook(textSpacing = spacing)) {
                val colors = LocalBorealisColors.current
                Box(
                    Modifier.fillMaxSize()
                        .background(
                            Brush.verticalGradient(listOf(colors.accentStrong, colors.page))
                        )
                ) {
                    StreamingLayer(
                        "Desktop",
                        "Gaming PC",
                        aStreaming(Panel.MENU, touchMode = null, gamepadMouse = false),
                        aPerf(),
                        RecordingIntents(),
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/menu_tv_spacing-$name.png")
    }
}
