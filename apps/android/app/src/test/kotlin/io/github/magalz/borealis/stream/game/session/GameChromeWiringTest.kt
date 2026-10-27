package io.github.magalz.borealis.stream.game.session

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
        while (type != null && LOOK_HOLDERS.any { type!!.name.startsWith(it) }) {
            for (field in type.declaredFields) {
                if (Modifier.isStatic(field.modifiers) || field.type.isPrimitive) continue
                field.isAccessible = true
                collectLooks(field.get(node), depth + 1, found)
            }
            type = type.superclass
        }
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

    private companion object {
        const val MAX_DEPTH = 6

        // The classes whose fields lead from the Compose view to the look: the state of the view,
        // the lambda of its content, and the chrome that made the lambda.
        val LOOK_HOLDERS = listOf("androidx.compose.", "io.github.magalz.borealis.")
    }
}
