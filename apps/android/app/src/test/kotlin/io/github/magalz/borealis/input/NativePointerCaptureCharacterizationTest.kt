package io.github.magalz.borealis.input

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The native pointer capture of `Game`: capture is the pointer capture of Android, asked for on
 * the stream view while a mouse or a touchpad is there. It is released with the cursor, and asked
 * for again half a second after the window gets focus back. Robolectric does not shadow pointer
 * capture, so the view of [CaptureRig] records the requests and the releases.
 */
@RunWith(RobolectricTestRunner::class)
class NativePointerCaptureCharacterizationTest {
    private val rig = CaptureRig()

    @Test
    fun `hiding the cursor sets the null icon, listens for devices and asks for capture`() {
        rig.connect(MOUSE, Sources.MOUSE)

        rig.provider.hideCursor()

        assertThat(rig.view.pointerIcon).isEqualTo(rig.nullIcon)
        assertThat(rig.listening()).isTrue()
        assertThat(rig.view.requests).isEqualTo(1)
        assertThat(rig.view.releases).isEqualTo(0)
    }

    @Test
    fun `the listener is registered before the capture is requested`() {
        // A device that changes when the capture starts has to reach the provider.
        rig.connect(MOUSE, Sources.MOUSE)
        var listeningAtRequest: Boolean? = null
        rig.view.onRequest = { listeningAtRequest = rig.listening() }

        rig.provider.hideCursor()

        assertThat(listeningAtRequest).isTrue()
        assertThat(rig.view.requests).isEqualTo(1)
    }

    @Test
    fun `hiding the cursor with no mouse still hides it and listens, but asks for nothing`() {
        rig.connect(KEYBOARD, Sources.KEYBOARD)

        rig.provider.hideCursor()

        assertThat(rig.view.pointerIcon).isEqualTo(rig.nullIcon)
        assertThat(rig.listening()).isTrue()
        assertThat(rig.view.requests).isEqualTo(0)
    }

    @Test
    fun `showing the cursor clears the icon, stops listening and releases the capture`() {
        rig.connect(MOUSE, Sources.MOUSE)
        rig.provider.hideCursor()

        rig.provider.showCursor()

        assertThat(rig.view.pointerIcon).isNull()
        assertThat(rig.listening()).isFalse()
        assertThat(rig.view.releases).isEqualTo(1)
    }

    @Test
    fun `the listener is gone before the capture is released`() {
        // Releasing the capture can make a touchpad report a change, which the provider would
        // answer by asking for the capture again.
        rig.provider.hideCursor()
        var listeningAtRelease: Boolean? = null
        rig.view.onRelease = { listeningAtRelease = rig.listening() }

        rig.provider.showCursor()

        assertThat(listeningAtRelease).isFalse()
        assertThat(rig.view.releases).isEqualTo(1)
    }

    @Test
    fun `showing a cursor that was never hidden still releases the capture`() {
        rig.provider.showCursor()

        assertThat(rig.view.releases).isEqualTo(1)
        assertThat(rig.view.pointerIcon).isNull()
    }

    @Test
    fun `enabling the capture hides the cursor and asks for capture, disabling releases it`() {
        rig.connect(MOUSE, Sources.MOUSE)
        assertThat(rig.provider.isCapturingActive()).isFalse()

        rig.provider.enableCapture()

        assertThat(rig.provider.isCapturingActive()).isTrue()
        assertThat(rig.view.pointerIcon).isEqualTo(rig.nullIcon)
        assertThat(rig.view.requests).isEqualTo(1)

        rig.provider.disableCapture()

        assertThat(rig.provider.isCapturingActive()).isFalse()
        assertThat(rig.view.pointerIcon).isNull()
        assertThat(rig.view.releases).isEqualTo(1)
        assertThat(rig.listening()).isFalse()
    }

    @Test
    fun `a mouse, a relative mouse and a touchpad can capture, and nothing else can`() {
        val capable =
            mapOf(
                "mouse" to (Sources.MOUSE to true),
                "relative mouse" to (Sources.MOUSE_RELATIVE to true),
                "touchpad" to (Sources.TOUCHPAD to true),
                "keyboard" to (Sources.KEYBOARD to false),
                "gamepad" to (Sources.GAMEPAD to false),
                "touchscreen" to (Sources.TOUCHSCREEN to false),
                "nothing" to (0 to false),
            )

        for ((name, case) in capable) {
            val (sources, expected) = case
            val fresh = CaptureRig()
            fresh.connect(MOUSE, sources)

            fresh.provider.hideCursor()

            assertWithMessage(name).that(fresh.view.requests > 0).isEqualTo(expected)
            fresh.disconnect(MOUSE)
        }
    }

    @Test
    fun `a touchscreen that also reports a mouse is skipped, except on ChromeOS`() {
        // Samsung reports a sec_touchpad device with the touchscreen, keyboard and mouse sources.
        val secTouchpad = Sources.TOUCHSCREEN or Sources.KEYBOARD or Sources.MOUSE
        rig.connect(MOUSE, secTouchpad)

        rig.provider.hideCursor()
        assertThat(rig.view.requests).isEqualTo(0)

        rig.onChromeOs()
        rig.provider.hideCursor()
        assertThat(rig.view.requests).isEqualTo(1)
    }

    @Test
    fun `a skipped touchscreen does not stop the search for another device`() {
        rig.connect(TOUCHSCREEN, Sources.TOUCHSCREEN or Sources.MOUSE)
        rig.connect(MOUSE, Sources.MOUSE)

        rig.provider.hideCursor()

        assertThat(rig.view.requests).isEqualTo(1)
    }

    @Test
    fun `a device that has only a touchscreen is no capture device on ChromeOS either`() {
        rig.onChromeOs()
        rig.connect(TOUCHSCREEN, Sources.TOUCHSCREEN)

        rig.provider.hideCursor()

        assertThat(rig.view.requests).isEqualTo(0)
    }

    @Test
    fun `regaining focus while capturing asks for capture again after 500 ms`() {
        rig.connect(MOUSE, Sources.MOUSE)
        rig.provider.enableCapture()
        assertThat(rig.view.requests).isEqualTo(1)

        rig.provider.onWindowFocusChanged(true)

        rig.advanceBy(499)
        assertThat(rig.view.requests).isEqualTo(1)
        rig.advanceBy(1)
        assertThat(rig.view.requests).isEqualTo(2)
    }

    @Test
    fun `each focus gain asks once, and the device is looked for when the delay ends`() {
        rig.provider.enableCapture()
        rig.provider.onWindowFocusChanged(true)
        rig.provider.onWindowFocusChanged(true)
        // The mouse is plugged in while the two requests wait.
        rig.connect(MOUSE, Sources.MOUSE)
        rig.advanceBy(500)

        assertThat(rig.view.requests).isEqualTo(2)
    }

    @Test
    fun `the request that waited is dropped when the mouse is gone by the end of the delay`() {
        rig.connect(MOUSE, Sources.MOUSE)
        rig.provider.enableCapture()
        rig.provider.onWindowFocusChanged(true)
        rig.disconnect(MOUSE)

        rig.advanceBy(500)

        assertThat(rig.view.requests).isEqualTo(1)
    }

    @Test
    fun `a request that waited is not cancelled when the capture is disabled meanwhile`() {
        // Pinned as the Java does it, not as a promise: the request looks for a device when its
        // delay ends, and does not look at whether the capture is still on.
        rig.connect(MOUSE, Sources.MOUSE)
        rig.provider.enableCapture()
        rig.provider.onWindowFocusChanged(true)
        rig.provider.disableCapture()
        val asked = rig.view.requests

        rig.advanceBy(500)

        assertThat(rig.view.requests).isEqualTo(asked + 1)
    }

    @Test
    fun `losing focus asks for nothing`() {
        rig.connect(MOUSE, Sources.MOUSE)
        rig.provider.enableCapture()

        rig.provider.onWindowFocusChanged(false)
        rig.advanceBy(1_000)

        assertThat(rig.view.requests).isEqualTo(1)
    }

    @Test
    fun `focus without capture asks for nothing`() {
        rig.connect(MOUSE, Sources.MOUSE)

        rig.provider.onWindowFocusChanged(true)
        rig.advanceBy(1_000)
        assertThat(rig.view.requests).isEqualTo(0)

        rig.provider.enableCapture()
        rig.provider.disableCapture()
        val asked = rig.view.requests
        rig.provider.onWindowFocusChanged(true)
        rig.advanceBy(1_000)
        assertThat(rig.view.requests).isEqualTo(asked)
    }

    @Test
    fun `focus while the cursor is shown asks for nothing, as capture would hide it`() {
        rig.connect(MOUSE, Sources.MOUSE)
        rig.provider.enableCapture()
        rig.provider.showCursor()

        rig.provider.onWindowFocusChanged(true)
        rig.advanceBy(1_000)

        assertThat(rig.view.requests).isEqualTo(1)
    }

    @Test
    fun `focus after the cursor is hidden again asks for capture`() {
        rig.connect(MOUSE, Sources.MOUSE)
        rig.provider.enableCapture()
        rig.provider.showCursor()
        rig.provider.hideCursor()
        assertThat(rig.view.requests).isEqualTo(2)

        rig.provider.onWindowFocusChanged(true)
        rig.advanceBy(500)

        assertThat(rig.view.requests).isEqualTo(3)
    }

    private companion object {
        const val MOUSE = 3
        const val KEYBOARD = 4
        const val TOUCHSCREEN = 2
    }
}
