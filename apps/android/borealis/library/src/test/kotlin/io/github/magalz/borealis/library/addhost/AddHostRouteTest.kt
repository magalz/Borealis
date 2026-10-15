package io.github.magalz.borealis.library.addhost

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.library.domain.AddHostResult
import io.github.magalz.borealis.library.domain.HostAddress
import io.github.magalz.borealis.library.testing.FakeHostRepository
import io.github.magalz.borealis.library.testing.PHONE
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The route with the real ViewModel over a fake repository (UC-008). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class AddHostRouteTest {
    @get:Rule val composeRule = createComposeRule()

    private val repository = FakeHostRepository()
    private var closed = 0
    private val added = mutableListOf<String>()

    @Test
    fun `typing an address and pressing Done adds the host and reports its name`() {
        show()

        composeRule.onNode(hasSetTextAction()).performTextInput("192.168.1.20")
        composeRule.onNode(hasSetTextAction()).performImeAction()
        composeRule.waitForIdle()

        assertThat(repository.addedAddresses).containsExactly(HostAddress("192.168.1.20"))
        assertThat(added).containsExactly("New PC")
    }

    @Test
    fun `an unreachable host shows the error and stays open`() {
        repository.addHostResult = AddHostResult.Unreachable
        show()

        composeRule.onNode(hasSetTextAction()).performTextInput("192.168.1.20")
        composeRule.onNode(hasText("Add host") and hasClickAction()).performClick()
        composeRule.waitForIdle()

        composeRule
            .onNodeWithText(
                "Unable to connect to the specified computer. " +
                    "Make sure the required ports are allowed through the firewall."
            )
            .assertExists()
        assertThat(added).isEmpty()
        assertThat(closed).isEqualTo(0)
    }

    @Test
    fun `an empty address shows the error and adds nothing`() {
        show()

        composeRule.onNode(hasText("Add host") and hasClickAction()).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Enter an IP address or a host name.").assertExists()
        assertThat(repository.addedAddresses).isEmpty()
    }

    @Test
    fun `Cancel closes and adds nothing`() {
        show()

        composeRule.onNode(hasSetTextAction()).performTextInput("192.168.1.20")
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        assertThat(closed).isEqualTo(1)
        assertThat(repository.addedAddresses).isEmpty()
        assertThat(added).isEmpty()
    }

    private fun show() {
        val viewModel = AddHostViewModel(repository)
        composeRule.setContent {
            AddHostRoute(onClose = { closed++ }, onAdded = { added += it }, viewModel = viewModel)
        }
        composeRule.waitForIdle()
    }
}
