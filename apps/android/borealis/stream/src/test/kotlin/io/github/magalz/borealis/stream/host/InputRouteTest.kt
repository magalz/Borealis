package io.github.magalz.borealis.stream.host

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.stream.ChromeMode
import org.junit.Test

class InputRouteTest {
    private val keys =
        mapOf(
            "D-pad up" to KeyEvent.KEYCODE_DPAD_UP,
            "D-pad center" to KeyEvent.KEYCODE_DPAD_CENTER,
            "Back" to KeyEvent.KEYCODE_BACK,
            "Enter" to KeyEvent.KEYCODE_ENTER,
            "gamepad A" to KeyEvent.KEYCODE_BUTTON_A,
            "gamepad B" to KeyEvent.KEYCODE_BUTTON_B,
            "letter" to KeyEvent.KEYCODE_A,
            "space" to KeyEvent.KEYCODE_SPACE,
            "Escape" to KeyEvent.KEYCODE_ESCAPE,
        )

    private val systemKeys =
        mapOf(
            "volume up" to KeyEvent.KEYCODE_VOLUME_UP,
            "volume down" to KeyEvent.KEYCODE_VOLUME_DOWN,
            "volume mute" to KeyEvent.KEYCODE_VOLUME_MUTE,
            "play/pause" to KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            "play" to KeyEvent.KEYCODE_MEDIA_PLAY,
            "pause" to KeyEvent.KEYCODE_MEDIA_PAUSE,
            "stop" to KeyEvent.KEYCODE_MEDIA_STOP,
            "next" to KeyEvent.KEYCODE_MEDIA_NEXT,
            "previous" to KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            "rewind" to KeyEvent.KEYCODE_MEDIA_REWIND,
            "fast forward" to KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
        )

    @Test
    fun `the modal chrome takes the navigation and the typing keys`() {
        for ((name, keyCode) in keys) {
            assertWithMessage(name).that(InputRoute.keyToChrome(ChromeMode.MODAL, keyCode)).isTrue()
        }
    }

    @Test
    fun `the modal chrome leaves the volume and the media keys to the system`() {
        for ((name, keyCode) in systemKeys) {
            assertWithMessage(name)
                .that(InputRoute.keyToChrome(ChromeMode.MODAL, keyCode))
                .isFalse()
        }
    }

    @Test
    fun `every other mode leaves every key to the stream`() {
        val others = ChromeMode.entries - ChromeMode.MODAL
        for (mode in others) {
            for ((name, keyCode) in keys + systemKeys) {
                assertWithMessage("$mode $name")
                    .that(InputRoute.keyToChrome(mode, keyCode))
                    .isFalse()
            }
        }
    }

    @Test
    fun `only the modal chrome takes motion events`() {
        for (mode in ChromeMode.entries) {
            assertWithMessage(mode.name)
                .that(InputRoute.motionToChrome(mode))
                .isEqualTo(mode == ChromeMode.MODAL)
        }
    }

    @Test
    fun `the route reads the mode again for every event`() {
        var mode = ChromeMode.IDLE
        val route = InputRoute { mode }
        val back = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)

        assertThat(route.toChrome(back)).isFalse()
        mode = ChromeMode.MODAL
        assertThat(route.toChrome(back)).isTrue()
    }
}
