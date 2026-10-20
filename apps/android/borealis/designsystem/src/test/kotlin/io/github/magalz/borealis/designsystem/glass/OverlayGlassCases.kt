package io.github.magalz.borealis.designsystem.glass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.PIXELS_PER_DP
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.Transparency
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What a dialog or a sheet does over a glass host, whatever it is. Where the host blurs its window
 * (the glass is on and the device can blur) the overlay draws no scrim, its panel is the tint of
 * the surface colour, and its text has the glass colours. Where it does not (API 30, Off, High
 * contrast or the plain page) the scrim, the solid panel and the plain colours stay as they were. A
 * subclass names one overlay.
 */
@RunWith(AndroidJUnit4::class)
abstract class OverlayGlassCases {
    @get:Rule val composeRule = createComposeRule()

    private var overlays: GlassOverlays? = null

    /** The form factor that the overlay is shown in; a subclass sets the window to match. */
    protected open val formFactor = FormFactor.Tv

    /** The title of the overlay, which is the first thing on its panel. */
    protected abstract val title: String

    /** A text of the overlay that is in the ink-3 colour. */
    protected abstract val secondaryText: String

    /** The overlay. */
    @Composable protected abstract fun Overlay()

    /** How much of the window behind the overlay it covers with a scrim, from 0 to 1. */
    protected open fun scrim(): Float = Color(windowImage().getPixel(CORNER_PX, CORNER_PX)).alpha

    // The host

    @Test
    fun `the overlay counts as open for as long as it is shown`() {
        show()

        assertThat(overlays?.isOpen).isTrue()
    }

    // Over a blurred window

    @Test
    fun `Standard draws no scrim, and the panel is 40 percent of the surface`() {
        show(BorealisLook())

        assertThat(scrim()).isWithin(TOLERANCE).of(0f)
        assertThat(panel().alpha).isWithin(TOLERANCE).of(0.4f)
    }

    @Test
    fun `Reduced draws no scrim, and the panel is 85 percent of the surface`() {
        show(BorealisLook(transparency = Transparency.Reduced))

        assertThat(scrim()).isWithin(TOLERANCE).of(0f)
        assertThat(panel().alpha).isWithin(TOLERANCE).of(0.85f)
    }

    @Test
    fun `the panel takes the tint that the floor of the background raises`() {
        show(BorealisLook(), BackgroundStyle.NorthernLights)

        assertThat(panel().alpha).isWithin(TOLERANCE).of(0.7f)
    }

    @Test
    fun `the secondary text is the glass ink-3 over Flowing light`() {
        show(BorealisLook(), BackgroundStyle.FlowingLight)

        assertThat(textColor(secondaryText)).isEqualTo(Color(0xFFA8ACB1))
    }

    // Where the window is not blurred

    @Test
    fun `Off keeps the scrim and the solid panel and the plain colours`() = assertAsBefore {
        show(BorealisLook(transparency = Transparency.Off))
    }

    @Test
    fun `High contrast keeps the scrim and the solid panel and the plain colours`() =
        assertAsBefore {
            show(BorealisLook(highContrast = true))
        }

    @Test
    fun `the plain page keeps the scrim and the solid panel and the plain colours`() =
        assertAsBefore {
            show(BorealisLook(), BackgroundStyle.Off)
        }

    @Test
    @Config(sdk = [API_NO_BLUR])
    fun `on API 30 the scrim and the solid panel and the plain colours stay`() = assertAsBefore {
        show(BorealisLook(), BackgroundStyle.FlowingLight)
    }

    // Helpers

    /** Runs [shown], and says that the overlay it shows is what it was before the glass. */
    private fun assertAsBefore(shown: () -> Unit) {
        shown()

        assertThat(scrim()).isGreaterThan(NO_SCRIM)
        assertThat(panel().alpha).isWithin(TOLERANCE).of(1f)
        assertThat(panel().red).isWithin(TOLERANCE).of(BorealisColors().surface.red)
        assertThat(textColor(secondaryText)).isEqualTo(BorealisColors().ink3)
    }

    private fun show(
        look: BorealisLook = BorealisLook(),
        style: BackgroundStyle = BackgroundStyle.AuroraCurtains,
    ) {
        composeRule.setContent {
            BorealisTheme(formFactor, look) {
                GlassHostOver(
                    style,
                    background = { Box(Modifier.fillMaxSize().background(Color.White)) },
                ) {
                    overlays = LocalGlassOverlays.current
                    Overlay()
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** The view of the overlay's own window, which is the second root. */
    @OptIn(InternalComposeUiApi::class)
    private fun windowView(): View =
        (composeRule.onAllNodes(isRoot())[1].fetchSemanticsNode().root as ViewRootForTest).view

    /**
     * What the overlay draws, with the alpha of each pixel. A screenshot of a node does not show a
     * dialog, so its view is drawn on a bitmap of its own.
     */
    private fun windowImage(): Bitmap {
        val view = windowView()
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        return image
    }

    /** The panel, a little above the title: the padding of the panel, with nothing drawn on it. */
    private fun panel(): Color {
        val title = composeRule.onNodeWithText(title).getUnclippedBoundsInRoot()
        return Color(
            windowImage()
                .getPixel(
                    (title.left.value * PIXELS_PER_DP).toInt(),
                    ((title.top - ABOVE_TITLE).value * PIXELS_PER_DP).toInt(),
                )
        )
    }

    private fun textColor(text: String): Color {
        val results = mutableListOf<TextLayoutResult>()
        composeRule
            .onNodeWithText(text, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(results)
        return results.single().layoutInput.style.color
    }

    /** How much the window of a dialog dims what is behind it, from 0 to 1. */
    protected fun windowDim(): Float {
        val view = windowView()
        val provider = view as? DialogWindowProvider ?: view.parent as DialogWindowProvider
        return provider.window.attributes.dimAmount
    }

    private companion object {
        const val API_NO_BLUR = 30
        const val TOLERANCE = 0.02f
        const val NO_SCRIM = 0.3f
        const val CORNER_PX = 4
        val ABOVE_TITLE = 8.dp
    }
}
