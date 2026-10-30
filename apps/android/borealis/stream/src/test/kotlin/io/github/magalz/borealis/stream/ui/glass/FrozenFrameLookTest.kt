package io.github.magalz.borealis.stream.ui.glass

import android.os.Build
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.ReduceMotion
import io.github.magalz.borealis.designsystem.theme.Transparency
import io.github.magalz.borealis.designsystem.theme.borealisColors
import org.junit.Test

/**
 * What the error popup draws over the frozen last frame of the stream (the owner's decision of
 * 2026-10-29): the panel is the glass of the menu (at the firm tint of a popup) where that would be
 * glass, and the solid surface otherwise; the frame is blurred wherever the device can blur and the
 * accessibility settings allow it, whatever the panel is, and shows sharp where they do not. The
 * frame is still and copied once, so Reduce motion and the battery saver change nothing.
 */
class FrozenFrameLookTest {
    private fun look(
        look: BorealisLook = BorealisLook(),
        sdk: Int = Build.VERSION_CODES.S,
        menuGlass: Boolean = true,
    ) = frozenFrameLook(look, borealisColors(look), sdk, menuGlass)

    // The glass of the menu, over the frame

    @Test
    fun `with the menu glass on, the panel is glass and the frame is blurred`() {
        val shown = look()

        assertThat(shown.panelIsGlass).isTrue()
        assertThat(shown.blur).isNotNull()
    }

    @Test
    fun `the frame is blurred as the menu glass is, 8 dp at Standard and 48 dp at Reduced`() {
        // The tint is not asked here: the frame is blurred with no tint, and the panel of the popup
        // takes the firm tint of a popup (VideoGlassSpecTest has the tints of the glass).
        assertThat(look().blur!!.blurDp).isEqualTo(8)
        assertThat(look(BorealisLook(transparency = Transparency.Reduced)).blur!!.blurDp)
            .isEqualTo(48)
    }

    // Solid around a solid panel

    @Test
    fun `with the menu glass on Solid, the panel is solid and the frame is still blurred`() {
        val shown = look(menuGlass = false)

        assertThat(shown.panelIsGlass).isFalse()
        assertThat(shown.blur).isNotNull()
    }

    // Where nothing can blur

    @Test
    fun `Transparency Off makes the panel solid and the frame sharp`() {
        val shown = look(BorealisLook(transparency = Transparency.Off))

        assertThat(shown.panelIsGlass).isFalse()
        assertThat(shown.blur).isNull()
    }

    @Test
    fun `high contrast makes the panel solid and the frame sharp`() {
        val shown = look(BorealisLook(highContrast = true))

        assertThat(shown.panelIsGlass).isFalse()
        assertThat(shown.blur).isNull()
    }

    @Test
    fun `below API 31 the panel is solid and the frame is sharp, under the scrim`() {
        val shown = look(sdk = Build.VERSION_CODES.R)

        assertThat(shown.panelIsGlass).isFalse()
        assertThat(shown.blur).isNull()
    }

    @Test
    fun `API 31 is where the blur starts`() {
        assertThat(look(sdk = Build.VERSION_CODES.S).blur).isNotNull()
        assertThat(look(sdk = Build.VERSION_CODES.R).blur).isNull()
    }

    // What changes nothing

    @Test
    fun `Reduce motion changes nothing, because the frame is still`() {
        val shown = look(BorealisLook(reduceMotion = ReduceMotion.On))

        assertThat(shown.panelIsGlass).isTrue()
        assertThat(shown.blur).isNotNull()
    }
}
