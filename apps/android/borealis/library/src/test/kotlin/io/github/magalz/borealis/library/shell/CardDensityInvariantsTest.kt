package io.github.magalz.borealis.library.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.DpRect
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.AcceptedFinding
import io.github.magalz.borealis.designsystem.testing.NoTouchOnTv
import io.github.magalz.borealis.designsystem.testing.assertAccessible
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.CardDensity
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.hosts.HostsUiState
import io.github.magalz.borealis.library.hosts.withHosts
import io.github.magalz.borealis.library.testing.AppsOf
import io.github.magalz.borealis.library.testing.HANDHELD
import io.github.magalz.borealis.library.testing.HostsOf
import io.github.magalz.borealis.library.testing.PHONE
import io.github.magalz.borealis.library.testing.TV
import io.github.magalz.borealis.library.testing.appTiles
import io.github.magalz.borealis.library.testing.hostCards
import io.github.magalz.borealis.library.testing.manyApps
import io.github.magalz.borealis.library.testing.manyHosts
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What every Card density keeps (ADR 0058, decision 7): a card or a tile that a finger hits is 48dp
 * wide and high, the cards keep 10dp or more between them, Google's accessibility checks find
 * nothing new, and the D-pad reaches each card of a row. The outline of a focused card is not
 * clipped in any density: `ThickFocusClippingTest` runs in each of them.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class CardDensityInvariantsTest(density: CardDensity) {
    @get:Rule val composeRule = createComposeRule()

    private val look = BorealisLook(cardDensity = density)
    private val appsShown = mutableStateOf(false)

    @Test
    @Config(qualifiers = PHONE)
    fun `Hosts and Apps pass the accessibility checks on a phone`() =
        assertAccessibleScreens(FormFactor.Phone)

    @Test
    @Config(qualifiers = HANDHELD)
    fun `Hosts and Apps pass the accessibility checks on a handheld`() =
        assertAccessibleScreens(FormFactor.Handheld)

    @Test
    @Config(qualifiers = TV)
    fun `Hosts and Apps pass the accessibility checks on a TV`() =
        assertAccessibleScreens(FormFactor.Tv, NoTouchOnTv)

    @Test
    @Config(qualifiers = PHONE)
    fun `a card and a tile are 48dp wide and high on a phone`() =
        assertTouchTargets(FormFactor.Phone)

    @Test
    @Config(qualifiers = WIDE_PHONE)
    fun `a card and a tile are 48dp wide and high on a wide phone`() =
        assertTouchTargets(FormFactor.Phone)

    @Test
    @Config(qualifiers = HANDHELD)
    fun `a card and a tile are 48dp wide and high on a handheld`() =
        assertTouchTargets(FormFactor.Handheld)

    @Test
    @Config(qualifiers = PHONE)
    fun `the cards and the tiles keep 10dp between them on a phone`() = assertGaps(FormFactor.Phone)

    @Test
    @Config(qualifiers = HANDHELD)
    fun `the cards and the tiles keep 10dp between them on a handheld`() =
        assertGaps(FormFactor.Handheld)

    @Test
    @Config(qualifiers = TV)
    fun `the cards and the tiles keep 10dp between them on a TV`() = assertGaps(FormFactor.Tv)

    @Test
    @Config(qualifiers = TV)
    fun `the D-pad crosses a row of host cards one card at a time`() {
        show(FormFactor.Tv)
        val perRow = composeRule.hostCards().let { cards -> cards.count { it.top == cards[0].top } }
        composeRule.onNodeWithTag("host:h1").requestFocus()

        for (card in 2..perRow) {
            composeRule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) }
            composeRule.onNodeWithTag("host:h$card").assertIsFocused()
        }
        composeRule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
        assertThat(focusedTag()).isEqualTo("host:h${perRow + perRow}")
    }

    /** Hosts first; [switchToApps] shows Apps in its place. */
    private fun show(formFactor: FormFactor, hosts: HostsUiState = manyHosts(HOSTS)) {
        val content: @Composable () -> Unit = {
            LibraryScaffold(
                formFactor,
                selected = RailSelection.Hosts,
                actions = RailActions({}, {}, {}),
                backgroundStyle = BackgroundStyle.Off,
                look = look,
            ) {
                if (appsShown.value) {
                    AppsOf(manyApps(APPS, running = 1), formFactor)
                } else {
                    HostsOf(hosts, formFactor)
                }
            }
        }
        if (formFactor == FormFactor.Phone) composeRule.setContent(content)
        else composeRule.setContentInKeyboardMode(content)
        composeRule.waitForIdle()
    }

    private fun switchToApps() {
        appsShown.value = true
        composeRule.waitForIdle()
    }

    private fun focusedTag(): String? =
        composeRule.onAllNodes(isFocused()).fetchSemanticsNodes().firstNotNullOfOrNull {
            it.config.getOrNull(SemanticsProperties.TestTag)
        }

    private fun assertAccessibleScreens(
        formFactor: FormFactor,
        vararg accepted: AcceptedFinding,
    ) {
        show(formFactor, withHosts)
        composeRule.assertAccessible(*accepted)

        switchToApps()
        composeRule.assertAccessible(*accepted)
    }

    private fun assertTouchTargets(formFactor: FormFactor) {
        show(formFactor)
        for (card in composeRule.hostCards()) assertAtLeast48(card)

        switchToApps()
        for (tile in composeRule.appTiles()) assertAtLeast48(tile)
    }

    private fun assertAtLeast48(bounds: DpRect) {
        assertThat(bounds.right.value - bounds.left.value).isAtLeast(ROUNDED_48)
        assertThat(bounds.bottom.value - bounds.top.value).isAtLeast(ROUNDED_48)
    }

    private fun assertGaps(formFactor: FormFactor) {
        show(formFactor)
        assertGaps(composeRule.hostCards())

        switchToApps()
        assertGaps(composeRule.appTiles())
    }

    /** The space between two cards of a row, and between two rows, is 10dp or more. */
    private fun assertGaps(cards: List<DpRect>) {
        val row = cards.filter { it.top == cards[0].top }
        for ((left, right) in row.zipWithNext()) {
            assertThat(right.left.value - left.right.value).isAtLeast(MIN_GAP)
        }
        val firstOfNextRow = cards.first { it.top > cards[0].top }
        assertThat(firstOfNextRow.top.value - row.maxOf { it.bottom.value }).isAtLeast(MIN_GAP)
    }

    private companion object {
        const val WIDE_PHONE = "w600dp-h960dp-xhdpi"
        const val HOSTS = 12
        const val APPS = 14

        /** 48dp, give or take the rounding of a pixel. */
        const val ROUNDED_48 = 47.99f
        const val MIN_GAP = 9.99f

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun densities(): List<CardDensity> = CardDensity.entries
    }
}
