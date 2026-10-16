package io.github.magalz.borealis.stream.ui.menu

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.ui.LayerFixture
import io.github.magalz.borealis.stream.ui.aStreaming
import io.github.magalz.borealis.stream.ui.starting.HANDHELD
import io.github.magalz.borealis.stream.ui.starting.PHONE
import io.github.magalz.borealis.stream.ui.starting.TV
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** UC-004-T3: the handle that pulls the menu open on a touch device. */
@RunWith(AndroidJUnit4::class)
class MenuHandleTest {
    @get:Rule val composeRule = createComposeRule()

    private fun layer(formFactor: FormFactor, panel: Panel = Panel.NONE) =
        LayerFixture(composeRule, formFactor, aStreaming(panel)).also { it.show() }

    private val handle
        get() = composeRule.onNodeWithContentDescription("Open menu")

    @Test
    @Config(qualifiers = PHONE)
    fun `a phone shows the handle`() {
        layer(FormFactor.Phone)

        handle.assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `a handheld shows the handle`() {
        layer(FormFactor.Handheld)

        handle.assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TV)
    fun `a TV has no handle`() {
        layer(FormFactor.Tv)

        handle.assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a tap on the handle opens the menu`() {
        val fixture = layer(FormFactor.Phone)

        handle.performClick()

        assertThat(fixture.intents.calls).containsExactly("openMenu")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `pulling the handle down opens the menu once`() {
        val fixture = layer(FormFactor.Phone)

        handle.performTouchInput { swipeDown() }

        assertThat(fixture.intents.calls).containsExactly("openMenu")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `pushing the handle up does not open the menu`() {
        val fixture = layer(FormFactor.Phone)

        handle.performTouchInput { swipeUp() }

        assertThat(fixture.intents.calls).isEmpty()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the handle sits below the top edge so the system bar swipe does not take it`() {
        layer(FormFactor.Phone)

        val top = handle.fetchSemanticsNode().boundsInRoot.top
        val density = composeRule.density.density

        assertThat(top).isAtLeast(MENU_HANDLE_TOP_OFFSET_DP * density - 1f)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the handle reports its bounds`() {
        val fixture = layer(FormFactor.Phone)

        assertThat(fixture.handleBounds.lastOrNull())
            .isEqualTo(handle.fetchSemanticsNode().boundsInRoot)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the handle reports no bounds once a panel hides it`() {
        val fixture = layer(FormFactor.Phone)

        fixture.phase = aStreaming(Panel.MENU)
        composeRule.waitForIdle()

        handle.assertDoesNotExist()
        assertThat(fixture.handleBounds.lastOrNull()).isNull()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the handle is hidden while the keyboard is open`() {
        layer(FormFactor.Phone, Panel.KEYBOARD)

        handle.assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = TV)
    fun `a TV never reports a handle`() {
        val fixture = layer(FormFactor.Tv)

        assertThat(fixture.handleBounds.filterNotNull()).isEmpty()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the handle reports its bounds again when it comes back`() {
        val fixture = layer(FormFactor.Phone, Panel.MENU)
        assertThat(fixture.handleBounds.lastOrNull()).isNull()

        fixture.phase = aStreaming(Panel.NONE)
        composeRule.waitForIdle()

        assertThat(fixture.handleBounds.lastOrNull()).isNotNull()
    }
}
