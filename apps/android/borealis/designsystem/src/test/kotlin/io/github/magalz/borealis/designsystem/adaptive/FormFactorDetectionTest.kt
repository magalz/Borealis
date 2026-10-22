package io.github.magalz.borealis.designsystem.adaptive

import android.content.res.Configuration
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.CardDensity
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Test

class FormFactorDetectionTest {
    @Test
    fun `the leanback feature makes a TV`() {
        assertThat(detect(isTvFeature = true)).isEqualTo(FormFactor.Tv)
    }

    @Test
    fun `the television UI mode makes a TV`() {
        val formFactor = detect(uiModeType = Configuration.UI_MODE_TYPE_TELEVISION)

        assertThat(formFactor).isEqualTo(FormFactor.Tv)
    }

    @Test
    fun `a TV stays a TV with a touch screen and a narrow window`() {
        val formFactor =
            detect(
                isTvFeature = true,
                widthDp = 400,
                touchscreen = Configuration.TOUCHSCREEN_FINGER,
            )

        assertThat(formFactor).isEqualTo(FormFactor.Tv)
    }

    @Test
    fun `a wide device without a touch screen is a handheld`() {
        val formFactor = detect(widthDp = 800, touchscreen = Configuration.TOUCHSCREEN_NOTOUCH)

        assertThat(formFactor).isEqualTo(FormFactor.Handheld)
    }

    @Test
    fun `a narrow device without a touch screen stays a phone`() {
        val formFactor = detect(widthDp = 599, touchscreen = Configuration.TOUCHSCREEN_NOTOUCH)

        assertThat(formFactor).isEqualTo(FormFactor.Phone)
    }

    @Test
    fun `a wide touch tablet without a game controller is a phone`() {
        assertThat(detect(widthDp = 840)).isEqualTo(FormFactor.Phone)
    }

    @Test
    fun `a phone is a phone`() {
        assertThat(detect(widthDp = 360)).isEqualTo(FormFactor.Phone)
    }

    @Test
    fun `app grids have 5 columns on a TV and 4 on a handheld`() {
        assertThat(appGridColumns(FormFactor.Tv, widthDp = 960, CardDensity.Comfortable))
            .isEqualTo(5)
        assertThat(appGridColumns(FormFactor.Handheld, widthDp = 640, CardDensity.Comfortable))
            .isEqualTo(4)
    }

    @Test
    fun `app grids have 3 columns on a narrow phone and 4 from 600dp`() {
        assertThat(appGridColumns(FormFactor.Phone, widthDp = 599, CardDensity.Comfortable))
            .isEqualTo(3)
        assertThat(appGridColumns(FormFactor.Phone, widthDp = 600, CardDensity.Comfortable))
            .isEqualTo(4)
    }

    @Test
    fun `host grids have 3 columns on a TV and 2 on a handheld`() {
        assertThat(hostGridColumns(FormFactor.Tv, widthDp = 960, CardDensity.Comfortable))
            .isEqualTo(3)
        assertThat(hostGridColumns(FormFactor.Handheld, widthDp = 640, CardDensity.Comfortable))
            .isEqualTo(2)
    }

    @Test
    fun `host grids have 1 column on a narrow phone and 2 from 600dp`() {
        assertThat(hostGridColumns(FormFactor.Phone, widthDp = 599, CardDensity.Comfortable))
            .isEqualTo(1)
        assertThat(hostGridColumns(FormFactor.Phone, widthDp = 600, CardDensity.Comfortable))
            .isEqualTo(2)
    }

    @Test
    fun `the window width is the container width in dp once the window has a size`() {
        assertThat(windowWidthDp(containerWidthPx = 1400, density = 2f, configurationWidthDp = 360))
            .isEqualTo(700)
    }

    @Test
    fun `before the window has a size the width comes from the configuration`() {
        assertThat(windowWidthDp(containerWidthPx = 0, density = 2f, configurationWidthDp = 700))
            .isEqualTo(700)
    }

    @Test
    fun `a wide touch phone with a connected gamepad is a handheld`() {
        assertThat(detect(widthDp = 700, gamepadConnected = true)).isEqualTo(FormFactor.Handheld)
        assertThat(detect(widthDp = 600, gamepadConnected = true)).isEqualTo(FormFactor.Handheld)
    }

    @Test
    fun `the same wide phone without a gamepad is a phone`() {
        assertThat(detect(widthDp = 700, gamepadConnected = false)).isEqualTo(FormFactor.Phone)
    }

    @Test
    fun `a narrow phone with a connected gamepad stays a phone`() {
        assertThat(detect(widthDp = 599, gamepadConnected = true)).isEqualTo(FormFactor.Phone)
    }

    @Test
    fun `a TV with a connected gamepad is a TV`() {
        val formFactor = detect(isTvFeature = true, widthDp = 960, gamepadConnected = true)

        assertThat(formFactor).isEqualTo(FormFactor.Tv)
    }

    /** A plain touch phone, no gamepad, unless a test says otherwise. */
    private fun detect(
        isTvFeature: Boolean = false,
        uiModeType: Int = Configuration.UI_MODE_TYPE_NORMAL,
        widthDp: Int = 360,
        touchscreen: Int = Configuration.TOUCHSCREEN_FINGER,
        gamepadConnected: Boolean = false,
    ) =
        formFactorOf(
            FormFactorFacts(isTvFeature, uiModeType, widthDp, touchscreen, gamepadConnected)
        )
}
