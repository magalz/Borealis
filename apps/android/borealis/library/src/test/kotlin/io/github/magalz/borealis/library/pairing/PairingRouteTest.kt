package io.github.magalz.borealis.library.pairing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.library.domain.PairingInstructions
import io.github.magalz.borealis.library.domain.PairingOutcome
import io.github.magalz.borealis.library.testing.FakePairingService
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The route wires the ViewModel to the screen and its effects to the navigation callbacks. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class PairingRouteTest {
    @get:Rule val composeRule = createComposeRule()
    private val service = FakePairingService()
    private val opened = mutableListOf<String>()
    private var closes = 0

    private fun show(hostName: String? = null) {
        service.prepared += PairingInstructions("4817", "https://192.0.2.10:47990", "Galaxy S22")
        val viewModel = PairingViewModel("host-1", service)
        composeRule.setContent {
            PairingRoute(
                hostId = "host-1",
                onOpenApps = { opened += it },
                onClose = { closes++ },
                hostName = hostName,
                viewModel = viewModel,
            )
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `the route shows the PIN of the ViewModel`() {
        show()

        composeRule.onNodeWithContentDescription("4, 8, 1, 7").assertIsDisplayed()
        composeRule.onNodeWithText("Pair with this PC").assertIsDisplayed()
    }

    @Test
    fun `the route shows the host name it is given`() {
        show(hostName = "Studio")

        composeRule.onNodeWithText("Pair with Studio").assertIsDisplayed()
    }

    @Test
    fun `a paired host opens its apps`() {
        show()

        service.pairCalls.single().outcome.complete(PairingOutcome.Paired)
        composeRule.waitForIdle()

        assertThat(opened).containsExactly("host-1")
        assertThat(closes).isEqualTo(0)
    }

    @Test
    fun `Cancel closes the screen and aborts the pairing`() {
        show()

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        assertThat(closes).isEqualTo(1)
        assertThat(opened).isEmpty()
        assertThat(service.pairCalls.single().cancelled).isTrue()
    }

    @Test
    fun `Try again after a failure asks the service for a new PIN`() {
        show()
        service.prepared += PairingInstructions("2093", null, "Galaxy S22")

        service.pairCalls.single().outcome.complete(PairingOutcome.PinWrong)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Try again").performClick()
        composeRule.waitForIdle()

        assertThat(service.prepareCalls).hasSize(2)
        composeRule.onNodeWithContentDescription("2, 0, 9, 3").assertIsDisplayed()
    }
}
