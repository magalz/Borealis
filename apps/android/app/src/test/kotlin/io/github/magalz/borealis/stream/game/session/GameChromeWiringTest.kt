package io.github.magalz.borealis.stream.game.session

import android.os.SystemClock
import androidx.compose.ui.platform.ComposeView
import com.google.common.truth.Truth.assertThat
import com.limelight.binding.input.ControllerHandler
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.TextSize
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.store.SettingsSnapshots
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.game.GameTestBase
import io.github.magalz.borealis.stream.game.phase
import io.github.magalz.borealis.stream.game.session
import io.github.magalz.borealis.stream.host.StreamChromeHost
import io.github.magalz.borealis.stream.input.ChromeKeySink
import io.github.magalz.borealis.stream.input.GamepadButtons
import io.github.magalz.borealis.stream.input.UsbChromeKeys
import java.lang.reflect.Modifier
import org.junit.Test
import org.robolectric.util.ReflectionHelpers

/** What the activity gives the stream chrome and its session, beyond what the rig pins. */
internal class GameChromeWiringTest : GameTestBase() {
    /**
     * The look that the chrome was built with. The chrome keeps it only inside the content of its
     * Compose view, so this looks in that content, a few levels deep, for the one look it holds.
     */
    private fun chromeLook(): BorealisLook {
        val host = rig.field<StreamChromeHost>("chromeHost")
        val composeView = ReflectionHelpers.getField<ComposeView>(host, "composeView")
        val content = ReflectionHelpers.getField<Any>(composeView, "content")
        val looks = mutableListOf<BorealisLook>()
        collectLooks(ReflectionHelpers.callInstanceMethod<Any>(content, "getValue"), 0, looks)
        return looks.distinct().single()
    }

    private fun collectLooks(node: Any?, depth: Int, found: MutableList<BorealisLook>) {
        if (node == null || depth > MAX_DEPTH) return
        if (node is BorealisLook) {
            found += node
            return
        }
        var type: Class<*>? = node.javaClass
        while (type != null && isLookHolder(type)) {
            for (field in type.declaredFields) {
                if (Modifier.isStatic(field.modifiers) || field.type.isPrimitive) continue
                field.isAccessible = true
                collectLooks(field.get(node), depth + 1, found)
            }
            type = type.superclass
        }
    }

    private fun isLookHolder(type: Class<*>): Boolean = LOOK_HOLDERS.any {
        type.name.startsWith(it)
    }

    private fun keyboardIsDocked(): Boolean {
        rig.session.openKeyboard()
        return (rig.phase as StreamPhase.Streaming).dockedKeyboard
    }

    @Test
    fun `the chrome gets the default look when the settings store none`() {
        rig.create()

        assertThat(chromeLook()).isEqualTo(BorealisLook())
    }

    @Test
    fun `the chrome gets the look that the settings store when the stream starts`() {
        val settings = SettingsSnapshots.repository(rig.application)
        settings.setNow(Settings.THICK_FOCUS, true)
        settings.setNow(Settings.TEXT_SIZE, "larger")

        rig.create()

        assertThat(chromeLook())
            .isEqualTo(BorealisLook(thickFocus = true, textSize = TextSize.Larger))
    }

    @Test
    fun `a setting that changes during the stream shows in the next stream, not in this one`() {
        rig.create()

        SettingsSnapshots.repository(rig.application).setNow(Settings.THICK_FOCUS, true)

        assertThat(chromeLook()).isEqualTo(BorealisLook())
    }

    @Test
    fun `the keyboard docks under the stream on a phone that has no gamepad`() {
        rig.streaming()

        assertThat(keyboardIsDocked()).isTrue()
    }

    @Test
    fun `the keyboard covers the stream when the USB driver holds a gamepad`() {
        rig.streaming()
        val handler = rig.field<ControllerHandler>("controllerHandler")
        handler.deviceAdded(RecordingPad(1, handler))

        assertThat(keyboardIsDocked()).isFalse()
    }

    @Test
    fun `a gamepad that is gone lets the keyboard dock again`() {
        rig.streaming()
        val handler = rig.field<ControllerHandler>("controllerHandler")
        val pad = RecordingPad(1, handler)
        handler.deviceAdded(pad)
        handler.deviceRemoved(pad)

        assertThat(keyboardIsDocked()).isTrue()
    }

    // The keys of a pad on the USB driver

    private val handler: ControllerHandler
        get() = rig.field("controllerHandler")

    private val chrome: StreamChromeHost
        get() = rig.field("chromeHost")

    private fun padKey(buttons: Int) =
        UsbChromeKeys.events(0, buttons, SystemClock.uptimeMillis()).single()

    /**
     * A phone's window starts in touch mode. The window of a unit test starts out of it, so the
     * test puts it in, where the window keeps the flag.
     */
    private fun startInTouchMode() {
        val attachInfo = ReflectionHelpers.getField<Any>(chrome, "mAttachInfo")
        ReflectionHelpers.setField(attachInfo, "mInTouchMode", true)
        assertThat(chrome.isInTouchMode).isTrue()
    }

    private fun chromeKeySink(): ChromeKeySink =
        checkNotNull(ReflectionHelpers.getField<ChromeKeySink?>(handler, "chromeKeySink"))

    @Test
    fun `a key of a USB pad reaches the modal chrome and takes the window out of touch mode`() {
        rig.streaming()
        rig.session.openMenu()
        startInTouchMode()

        val taken = chromeKeySink().onKey(padKey(GamepadButtons.DPAD_DOWN))

        assertThat(chrome.isInTouchMode).isFalse()
        // No row of the menu has the focus yet in this window, so nothing takes the key.
        assertThat(taken).isFalse()
    }

    @Test
    fun `a key of a USB pad does not take the window out of touch mode while the chrome is idle`() {
        rig.streaming()
        startInTouchMode()

        val taken = chromeKeySink().onKey(padKey(GamepadButtons.DPAD_DOWN))

        assertThat(taken).isFalse()
        assertThat(chrome.isInTouchMode).isTrue()
    }

    // The gamepad mouse

    @Test
    fun `the menu shows the gamepad mouse mode of a pad that has it on`() {
        rig.preference("checkbox_mouse_emulation", false)
        rig.streaming()
        handler.deviceAdded(RecordingPad(1, handler))
        handler.setMouseEmulation(true)
        assertThat((rig.phase as StreamPhase.Streaming).gamepadMouse).isFalse()

        rig.session.openMenu()

        assertThat((rig.phase as StreamPhase.Streaming).gamepadMouse).isTrue()
    }

    private companion object {
        const val MAX_DEPTH = 6

        // The classes whose fields lead from the Compose view to the look: the state of the view,
        // the lambda of its content, and the chrome that made the lambda.
        val LOOK_HOLDERS = listOf("androidx.compose.", "io.github.magalz.borealis.")
    }
}
