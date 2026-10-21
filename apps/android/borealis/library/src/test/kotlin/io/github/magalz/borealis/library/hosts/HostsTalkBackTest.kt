package io.github.magalz.borealis.library.hosts

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.headings
import io.github.magalz.borealis.designsystem.testing.readingOrder
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.shell.LibraryScaffold
import io.github.magalz.borealis.library.shell.RailActions
import io.github.magalz.borealis.library.shell.RailSelection
import io.github.magalz.borealis.library.testing.PHONE
import io.github.magalz.borealis.library.testing.TV
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What a screen reader is told on Hosts (WCAG 1.3.1, 2.4.3, 2.4.6 and 4.1.2): the title and the
 * section labels are headings, a card is read as one sentence, and the screen is read in the order
 * that it is drawn.
 */
@RunWith(AndroidJUnit4::class)
class HostsTalkBackTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = PHONE)
    fun `the title and the two section labels are headings on a phone`() {
        show(FormFactor.Phone)

        assertThat(composeRule.headings()).containsExactly("Hosts", "CONTINUE", "HOSTS").inOrder()
    }

    @Test
    @Config(qualifiers = TV)
    fun `the title and the two section labels are headings on a TV`() {
        show(FormFactor.Tv)

        assertThat(composeRule.headings()).containsExactly("Hosts", "CONTINUE", "HOSTS").inOrder()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `an online paired host is read as its name, status, address, capabilities and app`() {
        show(FormFactor.Phone)

        assertThat(description("host:desk"))
            .isEqualTo("Desk PC, Online, 192.168.1.20, HEVC 10-bit, HDR, Running Desktop")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `an offline host is read as its name, status and address`() {
        show(FormFactor.Phone)

        assertThat(description("host:living")).isEqualTo("Living Room PC, Offline, 192.168.1.31")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a host that is not paired is read with the note that it was found`() {
        show(FormFactor.Phone)

        assertThat(description("host:studio"))
            .isEqualTo("Studio, Not paired, Found on this network")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a host whose app has no name is read as running an app`() {
        show(FormFactor.Phone, withHostsNoContinue.copy(items = listOf(unnamedAppPc)))

        assertThat(description("host:unnamed")).isEqualTo("Den PC, Online, An app is running")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a phone is read from the top bar down to the cards`() {
        show(FormFactor.Phone)

        val order = composeRule.readingOrder()
        assertReadInOrder(order, "Hosts", "Add host", "Settings", "CONTINUE")
        assertReadInOrder(order, "CONTINUE", "Desktop", "HOSTS", "Desk PC", "Living Room PC")
        assertReadInOrder(order, "Living Room PC", "Studio")
    }

    @Test
    @Config(qualifiers = TV)
    fun `a TV is read from the rail to the title and the Continue card, then the hosts`() {
        show(FormFactor.Tv)

        val order = composeRule.readingOrder()
        // "Hosts" is said twice: by the item of the rail, then by the title.
        assertThat(order.count { it == "Hosts" }).isEqualTo(2)
        assertReadInOrder(order, "Hosts", "Add host", "Settings", "3 hosts", "CONTINUE")
        assertReadInOrder(order, "CONTINUE", "Desktop", "HOSTS", "Desk PC", "Living Room PC")
        assertReadInOrder(order, "Living Room PC", "Studio")
    }

    /**
     * Asserts that the first line of [order] that is, or starts with, each of [markers] comes after
     * the one of the marker before it, so that the markers are read in the order they are given.
     */
    private fun assertReadInOrder(order: List<String>, vararg markers: String) {
        val places = markers.map { marker ->
            order.indexOfFirst { it == marker || it.startsWith(marker) }
        }
        assertWithMessage("${markers.toList()} in ${order.joinToString(" // ")}")
            .that(places.all { it >= 0 } && places.zipWithNext().all { (a, b) -> a < b })
            .isTrue()
    }

    /** What a screen reader says for the card with [tag]: its content description. */
    private fun description(tag: String): String =
        composeRule
            .onNodeWithTag(tag)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.ContentDescription)
            ?.joinToString(", ")
            .orEmpty()

    private fun show(formFactor: FormFactor, state: HostsUiState = withHosts) {
        composeRule.setContent {
            LibraryScaffold(
                formFactor,
                RailSelection.Hosts,
                RailActions({}, {}, {}),
                backgroundStyle = BackgroundStyle.Off,
            ) {
                Hosts(formFactor, state)
            }
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun Hosts(formFactor: FormFactor, state: HostsUiState) {
        HostsScreen(
            state = state,
            formFactor = formFactor,
            snackbarHostState = SnackbarHostState(),
            onHostSelected = {},
            onContinue = {},
            onWake = {},
            onForget = {},
            onDetails = {},
            onDetailsDismissed = {},
            onAddHost = {},
            onSettings = {},
        )
    }
}
