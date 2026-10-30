package io.github.magalz.borealis.stream.ui.glass

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.testing.PHONE_LANDSCAPE
import io.github.magalz.borealis.stream.ui.aStreaming
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * When the menu glass copies the video (ADR 0061, decisions 1 and 3): 30 copies a second while the
 * menu or its Quit popup is open in the Streaming phase and the activity is started, one at a time,
 * and none otherwise.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE_LANDSCAPE)
class MenuGlassCopiesTest {
    @get:Rule val composeRule = createComposeRule()

    private fun fixture(source: FakeVideoSource = FakeVideoSource(completing = true)) =
        GlassFixture(composeRule, source)

    @Test
    fun `an open menu asks for 30 copies a second`() {
        val glass = fixture()
        glass.show()

        glass.run(1_000)

        assertThat(glass.source!!.requests.size).isAtLeast(28)
        assertThat(glass.source.requests.size).isAtMost(32)
    }

    @Test
    fun `a menu that is not open asks for no copy`() {
        val glass = fixture()
        glass.phase = aStreaming()
        glass.show()

        glass.run(1_000)

        assertThat(glass.source!!.requests).isEmpty()
    }

    @Test
    fun `the keyboard is not the menu and asks for none`() {
        val glass = fixture()
        glass.phase = aStreaming(Panel.KEYBOARD)
        glass.show()

        glass.run(1_000)

        assertThat(glass.source!!.requests).isEmpty()
    }

    @Test
    fun `closing the menu stops the copies`() {
        val glass = fixture()
        glass.show()
        glass.run(500)
        assertThat(glass.source!!.requests).isNotEmpty()

        glass.phase = aStreaming()
        glass.run(100)
        val asked = glass.source.requests.size
        glass.run(1_000)

        assertThat(glass.source.requests).hasSize(asked)
    }

    @Test
    fun `the Quit popup keeps the copies going and so does Cancel, until the menu closes`() {
        val glass = fixture()
        glass.show()
        glass.openQuitPopup()
        var asked = glass.source!!.requests.size

        glass.run(500)
        assertThat(glass.source.requests.size).isGreaterThan(asked)
        composeRule.onNodeWithText("Cancel").performScrollTo().performClick()
        glass.run(16)
        asked = glass.source.requests.size
        glass.run(500)
        assertThat(glass.source.requests.size).isGreaterThan(asked)

        glass.phase = aStreaming()
        glass.run(100)
        asked = glass.source.requests.size
        glass.run(1_000)
        assertThat(glass.source.requests).hasSize(asked)
    }

    @Test
    fun `a copy that is slow is not followed by another until it ends`() {
        val glass = fixture(FakeVideoSource())
        glass.show()

        glass.run(1_000)
        assertThat(glass.source!!.requests).hasSize(1)

        glass.source.finish()
        glass.run(100)

        assertThat(glass.source.requests).hasSize(2)
        assertThat(glass.source.mostInFlight).isEqualTo(1)
    }

    @Test
    fun `with no surface under the chrome nothing is asked for`() {
        val glass = fixture(FakeVideoSource(bounds = null, completing = true))
        glass.show()

        glass.run(1_000)

        assertThat(glass.source!!.requests).isEmpty()
    }

    @Test
    fun `a stopped activity asks for no copy, and a started one asks again`() {
        val glass = fixture()
        glass.show()
        glass.run(300)
        assertThat(glass.source!!.requests).isNotEmpty()

        glass.lifecycle.moveTo(Lifecycle.State.CREATED)
        glass.run(100)
        val asked = glass.source.requests.size
        glass.run(1_000)
        assertThat(glass.source.requests).hasSize(asked)

        glass.lifecycle.moveTo(Lifecycle.State.STARTED)
        glass.run(500)
        assertThat(glass.source.requests.size).isGreaterThan(asked)
    }
}
