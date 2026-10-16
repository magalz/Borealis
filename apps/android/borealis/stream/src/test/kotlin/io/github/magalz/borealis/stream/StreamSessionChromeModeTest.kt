package io.github.magalz.borealis.stream

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.host.launch.ConflictAnswer
import io.github.magalz.borealis.stream.testing.aDefaults
import io.github.magalz.borealis.stream.testing.aFailure
import io.github.magalz.borealis.stream.testing.aSession
import io.github.magalz.borealis.stream.testing.aSessionInfo
import org.junit.Test

class StreamSessionChromeModeTest {
    private fun streaming(
        overlay: OverlayMode = OverlayMode.OFF,
        dockedKeyboard: Boolean = false,
    ): StreamSession =
        aSession(defaults = aDefaults.copy(overlay = overlay), dockedKeyboard = dockedKeyboard)
            .also {
                it.onConnectionStarted(aSessionInfo)
                it.onFirstFrame()
            }

    @Test
    fun `starting is modal`() {
        assertThat(aSession().chromeMode).isEqualTo(ChromeMode.MODAL)
    }

    @Test
    fun `a conflict is modal`() {
        val session = aSession()

        session.onConflict("Steam", "Desktop", sameApp = false, answer = ConflictAnswer())

        assertThat(session.chromeMode).isEqualTo(ChromeMode.MODAL)
    }

    @Test
    fun `a failure is modal`() {
        val session = aSession()

        session.onFailed(aFailure)

        assertThat(session.chromeMode).isEqualTo(ChromeMode.MODAL)
    }

    @Test
    fun `the menu is modal`() {
        val session = streaming()

        session.openMenu()

        assertThat(session.chromeMode).isEqualTo(ChromeMode.MODAL)
    }

    @Test
    fun `a docked keyboard is docked`() {
        val session = streaming(dockedKeyboard = true)

        session.openKeyboard()

        assertThat(session.chromeMode).isEqualTo(ChromeMode.DOCKED)
    }

    @Test
    fun `a keyboard that is not docked is modal`() {
        val session = streaming(dockedKeyboard = false)

        session.openKeyboard()

        assertThat(session.chromeMode).isEqualTo(ChromeMode.MODAL)
    }

    @Test
    fun `streaming with the overlay off is idle`() {
        assertThat(streaming(OverlayMode.OFF).chromeMode).isEqualTo(ChromeMode.IDLE)
    }

    @Test
    fun `streaming with an overlay is overlay`() {
        assertThat(streaming(OverlayMode.COMPACT).chromeMode).isEqualTo(ChromeMode.OVERLAY)
        assertThat(streaming(OverlayMode.DETAILED).chromeMode).isEqualTo(ChromeMode.OVERLAY)
    }

    @Test
    fun `closing a panel returns to the overlay mode`() {
        val session = streaming(OverlayMode.COMPACT)
        session.openMenu()

        session.closePanel()

        assertThat(session.chromeMode).isEqualTo(ChromeMode.OVERLAY)
    }

    @Test
    fun `changing the overlay changes the mode`() {
        val session = streaming(OverlayMode.OFF)

        session.setOverlay(OverlayMode.DETAILED)

        assertThat(session.chromeMode).isEqualTo(ChromeMode.OVERLAY)
    }

    @Test
    fun `picture in picture hides the chrome in every phase`() {
        val starting = aSession()
        val menu = streaming().also { it.openMenu() }
        val failed = aSession().also { it.onFailed(aFailure) }

        for (session in listOf(starting, menu, failed, streaming(OverlayMode.COMPACT))) {
            session.onPip(true)
            assertThat(session.chromeMode).isEqualTo(ChromeMode.HIDDEN)
        }
    }

    @Test
    fun `leaving picture in picture brings the chrome back`() {
        val session = streaming(OverlayMode.COMPACT)
        session.onPip(true)

        session.onPip(false)

        assertThat(session.chromeMode).isEqualTo(ChromeMode.OVERLAY)
    }
}
