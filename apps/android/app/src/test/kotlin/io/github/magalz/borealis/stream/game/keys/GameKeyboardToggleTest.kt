package io.github.magalz.borealis.stream.game.keys

import android.view.inputmethod.InputMethodManager
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.game.GameTestBase
import io.github.magalz.borealis.stream.game.phase
import org.junit.Test
import org.robolectric.Shadows.shadowOf

/**
 * The three-finger tap asks the activity to toggle the keyboard: the Borealis keyboard of the
 * stream opens and closes, and the Android keyboard is only ever hidden from here, never toggled.
 */
internal class GameKeyboardToggleTest : GameTestBase() {
    private val panel: Panel
        get() = (rig.phase as StreamPhase.Streaming).panel

    private val inputMethods
        get() = rig.application.getSystemService(InputMethodManager::class.java)

    private val androidKeyboard
        get() = shadowOf(inputMethods)

    private fun showAndroidKeyboard() {
        rig.androidKeyboardShown(true)
        inputMethods.showSoftInput(rig.game.window.decorView, 0)
        assertThat(androidKeyboard.isSoftInputVisible).isTrue()
    }

    @Test
    fun `the keyboard toggle opens the Borealis keyboard, and closes it the next time`() {
        rig.streaming()
        assertThat(panel).isEqualTo(Panel.NONE)

        rig.game.toggleKeyboard()

        assertThat(panel).isEqualTo(Panel.KEYBOARD)

        rig.game.toggleKeyboard()

        assertThat(panel).isEqualTo(Panel.NONE)
    }

    @Test
    fun `the keyboard toggle never toggles the Android keyboard`() {
        rig.streaming()

        // The Android keyboard opens from the Borealis keyboard, not from the three-finger tap. A
        // toggle would show it on the first tap and hide it on the second.
        rig.game.toggleKeyboard()
        assertThat(androidKeyboard.isSoftInputVisible).isFalse()

        rig.game.toggleKeyboard()
        assertThat(androidKeyboard.isSoftInputVisible).isFalse()
    }

    @Test
    fun `the keyboard toggle hides the Android keyboard when it shows, and leaves the Borealis keyboard as it is`() {
        rig.streaming()
        showAndroidKeyboard()

        rig.game.toggleKeyboard()

        assertThat(androidKeyboard.isSoftInputVisible).isFalse()
        assertThat(panel).isEqualTo(Panel.NONE)
    }

    @Test
    fun `the keyboard toggle hides the Android keyboard while the Borealis keyboard is open, and keeps it open`() {
        rig.streaming()
        rig.game.toggleKeyboard()
        assertThat(panel).isEqualTo(Panel.KEYBOARD)
        showAndroidKeyboard()

        rig.game.toggleKeyboard()

        assertThat(androidKeyboard.isSoftInputVisible).isFalse()
        assertThat(panel).isEqualTo(Panel.KEYBOARD)
    }
}
