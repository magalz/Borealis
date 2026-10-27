package io.github.magalz.borealis.stream

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.testing.unconfinedScope
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Test

class GamepadMouseSyncTest {
    private val actions = RecordingStreamActions()
    private val scope = unconfinedScope()
    private var onThePad = false

    @After fun tearDown() = scope.cancel()

    private fun gamepadMouse(session: StreamSession) =
        (session.state.value.phase as StreamPhase.Streaming).gamepadMouse

    private fun bound(gamepadMouse: Boolean?): StreamSession {
        val session = streamingSession(gamepadMouse = gamepadMouse, actions = actions)
        GamepadMouseSync.bind(scope, session) { onThePad }
        return session
    }

    @Test
    fun `opening the menu shows what the pad switched on with the Start hold`() {
        val session = bound(gamepadMouse = false)
        onThePad = true

        session.openMenu()

        assertThat(gamepadMouse(session)).isTrue()
        assertThat(actions.calls).containsExactly("gamepadMouse true")
    }

    @Test
    fun `opening the menu shows what the pad switched off`() {
        val session = bound(gamepadMouse = true)
        onThePad = false

        session.openMenu()

        assertThat(gamepadMouse(session)).isFalse()
    }

    @Test
    fun `a menu that already shows what the pad has is left alone`() {
        val session = bound(gamepadMouse = true)
        onThePad = true

        session.openMenu()

        assertThat(actions.calls).isEmpty()
    }

    @Test
    fun `every opening of the menu reads the pad again`() {
        val session = bound(gamepadMouse = false)
        session.openMenu()
        session.closePanel()
        onThePad = true

        session.openMenu()

        assertThat(gamepadMouse(session)).isTrue()
    }

    @Test
    fun `the keyboard panel does not read the pad`() {
        val session = bound(gamepadMouse = false)
        onThePad = true

        session.openKeyboard()

        assertThat(gamepadMouse(session)).isFalse()
    }

    @Test
    fun `without a gamepad choice to offer there is nothing to keep in step`() {
        val session = bound(gamepadMouse = null)
        onThePad = true

        session.openMenu()

        assertThat(gamepadMouse(session)).isNull()
        assertThat(actions.calls).isEmpty()
    }
}
