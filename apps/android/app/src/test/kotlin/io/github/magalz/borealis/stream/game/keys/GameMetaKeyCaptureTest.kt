package io.github.magalz.borealis.stream.game.keys

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import com.samsung.android.view.SemWindowManager
import io.github.magalz.borealis.stream.game.GameTestBase
import org.junit.After
import org.junit.Test

/**
 * The meta keys (Windows and the like) are captured with the input, through the window manager of
 * Samsung where the device has one: on the devices of the stream's SDK level, the activity asks it
 * by reflection, when it grabs the input and when it lets go.
 */
internal class GameMetaKeyCaptureTest : GameTestBase() {
    @After
    fun forgetTheWindowManager() {
        SemWindowManager.reset()
    }

    @Test
    fun `grabbing and letting the input go asks the window manager for the meta keys`() {
        rig.streaming()
        SemWindowManager.available = true
        val component = rig.game.componentName

        rig.chord(KeyEvent.KEYCODE_Z)
        idle(250)
        rig.chord(KeyEvent.KEYCODE_Z)
        idle(250)

        assertThat(SemWindowManager.requests)
            .containsExactly(component to false, component to true)
            .inOrder()
    }

    @Test
    fun `the chrome that takes the input lets the meta keys go, and gives them back with the grab`() {
        rig.streaming()
        SemWindowManager.available = true
        val component = rig.game.componentName

        rig.openChrome()
        rig.closeChrome()

        assertThat(SemWindowManager.requests)
            .containsExactly(component to false, component to true)
            .inOrder()
    }

    @Test
    fun `a device with no window manager to give is not asked, and the grab goes on`() {
        rig.streaming()
        SemWindowManager.available = false

        rig.chord(KeyEvent.KEYCODE_Z)
        idle(250)

        assertThat(SemWindowManager.requests).isEmpty()
        assertThat(rig.pointerCaptured).isFalse()
    }

    @Test
    fun `a window manager that fails does not stop the grab`() {
        rig.streaming()
        SemWindowManager.available = true
        SemWindowManager.failure = IllegalStateException("the window manager is not ready")

        rig.chord(KeyEvent.KEYCODE_Z)
        idle(250)

        assertThat(SemWindowManager.requests).hasSize(1)
        assertThat(rig.pointerCaptured).isFalse()
    }
}
