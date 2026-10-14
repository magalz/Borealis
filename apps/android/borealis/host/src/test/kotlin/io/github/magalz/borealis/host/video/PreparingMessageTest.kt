package io.github.magalz.borealis.host.video

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PreparingMessageTest {
    private class FakeOverlay : StartupOverlay {
        var shown = 0
        var cleared = 0
        var visible = false

        override fun showPreparing() {
            shown++
            visible = true
        }

        override fun clearPreparing() {
            cleared++
            visible = false
        }
    }

    private val overlay = FakeOverlay()
    private val message = PreparingMessage(overlay)

    @Test
    fun `show displays the message`() {
        message.show()

        assertThat(overlay.visible).isTrue()
    }

    @Test
    fun `a second show does not touch the overlay again`() {
        message.show()
        message.show()

        assertThat(overlay.shown).isEqualTo(1)
    }

    @Test
    fun `hide removes the message`() {
        message.show()

        message.hide()

        assertThat(overlay.visible).isFalse()
    }

    @Test
    fun `hide without a visible message leaves the overlay alone`() {
        message.hide()

        assertThat(overlay.cleared).isEqualTo(0)
    }

    @Test
    fun `a message hidden before it was shown is never shown`() {
        message.hide()

        message.show()

        assertThat(overlay.shown).isEqualTo(0)
    }

    @Test
    fun `a message hidden once stays hidden`() {
        message.show()
        message.hide()

        message.show()

        assertThat(overlay.shown).isEqualTo(1)
        assertThat(overlay.visible).isFalse()
    }

    @Test
    fun `a warning takes precedence and is not removed when the frame arrives`() {
        message.show()
        message.onWarning()

        message.hide()

        assertThat(overlay.cleared).isEqualTo(0)
    }

    @Test
    fun `clearing the warning while still preparing shows the message again`() {
        message.show()
        message.onWarning()

        message.onWarningCleared()

        assertThat(overlay.shown).isEqualTo(2)
        assertThat(overlay.visible).isTrue()
    }

    @Test
    fun `clearing the warning after the frame arrived leaves the overlay to the connection status`() {
        message.show()
        message.onWarning()
        message.hide()

        message.onWarningCleared()

        assertThat(overlay.shown).isEqualTo(1)
        assertThat(overlay.cleared).isEqualTo(0)
    }

    @Test
    fun `a warning before show keeps the message from covering it`() {
        message.onWarning()

        message.show()

        assertThat(overlay.shown).isEqualTo(0)
    }

    @Test
    fun `a message waiting behind a warning appears when the warning clears`() {
        message.onWarning()
        message.show()

        message.onWarningCleared()

        assertThat(overlay.visible).isTrue()
    }
}
