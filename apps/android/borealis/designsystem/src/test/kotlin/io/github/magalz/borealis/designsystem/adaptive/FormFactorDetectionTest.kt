package io.github.magalz.borealis.designsystem.adaptive

import android.content.res.Configuration
import com.google.common.truth.Truth.assertThat
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
    fun `a wide device with a D-pad is a handheld`() {
        val formFactor = detect(widthDp = 640, navigation = Configuration.NAVIGATION_DPAD)

        assertThat(formFactor).isEqualTo(FormFactor.Handheld)
    }

    @Test
    fun `a wide device with a trackball or wheel is a handheld`() {
        assertThat(detect(widthDp = 600, navigation = Configuration.NAVIGATION_TRACKBALL))
            .isEqualTo(FormFactor.Handheld)
        assertThat(detect(widthDp = 600, navigation = Configuration.NAVIGATION_WHEEL))
            .isEqualTo(FormFactor.Handheld)
    }

    @Test
    fun `a wide device without a touch screen is a handheld`() {
        val formFactor = detect(widthDp = 800, touchscreen = Configuration.TOUCHSCREEN_NOTOUCH)

        assertThat(formFactor).isEqualTo(FormFactor.Handheld)
    }

    @Test
    fun `a narrow device with a D-pad stays a phone`() {
        val formFactor = detect(widthDp = 599, navigation = Configuration.NAVIGATION_DPAD)

        assertThat(formFactor).isEqualTo(FormFactor.Phone)
    }

    @Test
    fun `a wide touch device without a D-pad is a phone`() {
        assertThat(detect(widthDp = 840)).isEqualTo(FormFactor.Phone)
    }

    @Test
    fun `a phone is a phone`() {
        assertThat(detect(widthDp = 360)).isEqualTo(FormFactor.Phone)
    }

    @Test
    fun `app grids have 5 columns on a TV and 4 on a handheld`() {
        assertThat(appGridColumns(FormFactor.Tv, widthDp = 960)).isEqualTo(5)
        assertThat(appGridColumns(FormFactor.Handheld, widthDp = 640)).isEqualTo(4)
    }

    @Test
    fun `app grids have 3 columns on a narrow phone and 4 from 600dp`() {
        assertThat(appGridColumns(FormFactor.Phone, widthDp = 599)).isEqualTo(3)
        assertThat(appGridColumns(FormFactor.Phone, widthDp = 600)).isEqualTo(4)
    }

    @Test
    fun `host grids have 3 columns on a TV and 2 on a handheld`() {
        assertThat(hostGridColumns(FormFactor.Tv, widthDp = 960)).isEqualTo(3)
        assertThat(hostGridColumns(FormFactor.Handheld, widthDp = 640)).isEqualTo(2)
    }

    @Test
    fun `host grids have 1 column on a narrow phone and 2 from 600dp`() {
        assertThat(hostGridColumns(FormFactor.Phone, widthDp = 599)).isEqualTo(1)
        assertThat(hostGridColumns(FormFactor.Phone, widthDp = 600)).isEqualTo(2)
    }

    /** A plain touch phone unless a test says otherwise. */
    private fun detect(
        isTvFeature: Boolean = false,
        uiModeType: Int = Configuration.UI_MODE_TYPE_NORMAL,
        widthDp: Int = 360,
        touchscreen: Int = Configuration.TOUCHSCREEN_FINGER,
        navigation: Int = Configuration.NAVIGATION_NONAV,
    ) = formFactorOf(isTvFeature, uiModeType, widthDp, touchscreen, navigation)
}
