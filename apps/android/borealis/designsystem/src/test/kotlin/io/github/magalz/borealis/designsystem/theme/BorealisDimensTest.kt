package io.github.magalz.borealis.designsystem.theme

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BorealisDimensTest {
    @Test
    fun `the three dialog widths are 420, 560 and 720`() {
        assertThat(DialogWidth.Compact.width).isEqualTo(420.dp)
        assertThat(DialogWidth.Standard.width).isEqualTo(560.dp)
        assertThat(DialogWidth.Wide.width).isEqualTo(720.dp)
    }

    @Test
    fun `a full screen caps its content at 560`() {
        assertThat(BorealisDimens.contentMaxWidth).isEqualTo(560.dp)
    }

    @Test
    fun `the focus outline reaches 5dp outside its element`() {
        assertThat(BorealisDimens.outlineReach).isEqualTo(5.dp)
    }

    @Test
    fun `a list of focusable rows keeps 8dp around it and 10dp between its rows`() {
        assertThat(BorealisDimens.listPadding).isEqualTo(8.dp)
        assertThat(BorealisDimens.listGap).isEqualTo(10.dp)
    }

    @Test
    fun `the padding of a list holds the outline of a row, and the gap holds two outlines`() {
        assertThat(BorealisDimens.listPadding).isAtLeast(BorealisDimens.outlineReach)
        assertThat(BorealisDimens.listGap).isAtLeast(BorealisDimens.outlineReach * 2)
    }

    @Test
    fun `the thicker focus outline reaches 7dp outside its element`() {
        assertThat(BorealisDimens.thickOutlineReach).isEqualTo(7.dp)
    }

    @Test
    fun `the padding and the gap of a list hold the thicker outline too`() {
        assertThat(BorealisDimens.listPadding).isAtLeast(BorealisDimens.thickOutlineReach)
        assertThat(BorealisDimens.listGap).isAtLeast(BorealisDimens.thickOutlineReach)
    }

    @Test
    fun `the navigation is 220 wide on a TV and 200 on a handheld`() {
        assertThat(BorealisDimens.navWidth(FormFactor.Tv)).isEqualTo(220.dp)
        assertThat(BorealisDimens.navWidth(FormFactor.Handheld)).isEqualTo(200.dp)
    }

    @Test
    fun `a dialog is as wide as its token when the window has room`() {
        DialogWidth.entries.forEach {
            assertThat(BorealisDimens.dialogWidth(it, 1280.dp)).isEqualTo(it.width)
        }
    }

    @Test
    fun `a dialog keeps 16dp from both edges of a narrow window`() {
        DialogWidth.entries.forEach {
            assertThat(BorealisDimens.dialogWidth(it, 360.dp)).isEqualTo(328.dp)
        }
    }

    @Test
    fun `a dialog narrower than the window is not widened to the edge margin`() {
        assertThat(BorealisDimens.dialogWidth(DialogWidth.Compact, 440.dp)).isEqualTo(408.dp)
        assertThat(BorealisDimens.dialogWidth(DialogWidth.Compact, 480.dp)).isEqualTo(420.dp)
    }

    @Test
    fun `a sheet on a TV is a compact dialog`() {
        assertThat(BorealisDimens.sheetWidth(FormFactor.Tv, 1920.dp)).isEqualTo(420.dp)
        assertThat(BorealisDimens.sheetWidth(FormFactor.Tv, 960.dp)).isEqualTo(420.dp)
    }

    @Test
    fun `a sheet on a handheld is 60 percent of the window`() {
        assertThat(BorealisDimens.sheetWidth(FormFactor.Handheld, 800.dp)).isEqualTo(480.dp)
    }

    @Test
    fun `a sheet on a handheld stays between the compact and standard widths`() {
        assertThat(BorealisDimens.sheetWidth(FormFactor.Handheld, 640.dp)).isEqualTo(420.dp)
        assertThat(BorealisDimens.sheetWidth(FormFactor.Handheld, 1600.dp)).isEqualTo(560.dp)
    }

    @Test
    fun `a sheet on a handheld never touches the window edges`() {
        assertThat(BorealisDimens.sheetWidth(FormFactor.Handheld, 400.dp)).isEqualTo(368.dp)
    }
}
