package io.github.magalz.borealis.stream.game

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.common.truth.Truth.assertThat
import com.limelight.R
import com.limelight.binding.input.ControllerHandler
import com.limelight.binding.input.KeyboardTranslator
import com.limelight.binding.input.touch.AbsoluteTouchContext
import com.limelight.binding.input.touch.RelativeTouchContext
import com.limelight.binding.input.virtual_controller.VirtualController
import com.limelight.preferences.PreferenceConfiguration
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.PerfSnapshot
import io.github.magalz.borealis.stream.PerfSnapshotSink
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.host.StreamChromeHost
import io.github.magalz.borealis.stream.input.ChromeKeySink
import io.github.magalz.borealis.stream.input.MenuChordListener
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.util.ReflectionHelpers

/** What `Game.onCreate` constructs and how it wires the parts to each other. */
internal class GameConstructionTest : GameTestBase() {
    private fun inputDeviceListeners(): List<Any> {
        val global = Class.forName("android.hardware.input.InputManagerGlobal")
        val instance = ReflectionHelpers.callStaticMethod<Any>(global, "getInstance")
        return ReflectionHelpers.getField<List<Any>>(instance, "mInputDeviceListeners").map {
            ReflectionHelpers.getField(it, "mListener")
        }
    }

    private fun streaming(): StreamPhase.Streaming {
        rig.streaming()
        return rig.phase as StreamPhase.Streaming
    }

    private fun controllerHandler() = rig.field<ControllerHandler>("controllerHandler")

    @Test
    fun `the stream chrome covers the stream view and takes the input first`() {
        rig.create()

        val content = rig.game.findViewById<ViewGroup>(android.R.id.content)
        val chrome = content.getChildAt(content.childCount - 1)
        assertThat(chrome).isSameInstanceAs(rig.field<StreamChromeHost>("chromeHost"))
        assertThat(chrome.layoutParams.width).isEqualTo(FrameLayout.LayoutParams.MATCH_PARENT)
        assertThat(chrome.layoutParams.height).isEqualTo(FrameLayout.LayoutParams.MATCH_PARENT)
        // The chrome is modal while the stream starts: it holds the input and nothing is grabbed.
        assertThat(rig.field<Boolean>("grabbedInput")).isFalse()
    }

    @Test
    fun `the keyboard translator and the controller handler listen for input devices`() {
        rig.create()

        val listeners = inputDeviceListeners()
        assertThat(listeners).contains(rig.field<KeyboardTranslator>("keyboardTranslator"))
        assertThat(listeners).contains(controllerHandler())
    }

    @Test
    fun `the keyboard translator stops listening when the activity is destroyed`() {
        rig.create()
        val translator = rig.field<KeyboardTranslator>("keyboardTranslator")

        rig.destroyActivity()

        assertThat(inputDeviceListeners()).doesNotContain(translator)
    }

    @Test
    fun `a finger starts as a trackpad`() {
        rig.create()

        val contexts = rig.field<Array<Any?>>("touchContextMap")
        assertThat(contexts.map { it?.javaClass })
            .containsExactly(RelativeTouchContext::class.java, RelativeTouchContext::class.java)
    }

    @Test
    fun `the direct touch setting builds absolute touch contexts`() {
        rig.preference("checkbox_touchscreen_trackpad", false)

        rig.create()

        val contexts = rig.field<Array<Any?>>("touchContextMap")
        assertThat(contexts.map { it?.javaClass })
            .containsExactly(AbsoluteTouchContext::class.java, AbsoluteTouchContext::class.java)
    }

    @Test
    fun `the on-screen controller is built on the parent of the stream view when it is on`() {
        rig.preference("checkbox_show_onscreen_controls", true)

        rig.create()

        val controller = checkNotNull(rig.field<VirtualController?>("virtualController"))
        val parent = rig.game.findViewById<View>(R.id.surfaceView).parent
        assertThat(parent).isInstanceOf(FrameLayout::class.java)
        // Its elements and its configure button are views of that parent, and they report to the
        // controller handler.
        assertThat(controller.elements).isNotEmpty()
        assertThat(controller.elements.map { it.parent }.distinct()).containsExactly(parent)
        assertThat(ReflectionHelpers.getField<View>(controller, "buttonConfigure").parent)
            .isSameInstanceAs(parent)
        assertThat(ReflectionHelpers.getField<Any?>(controller, "controllerHandler"))
            .isSameInstanceAs(controllerHandler())
    }

    @Test
    fun `without the on-screen controller setting there is none`() {
        rig.create()

        assertThat(rig.field<VirtualController?>("virtualController")).isNull()
    }

    @Test
    fun `the renderer reads the settings of the activity`() {
        rig.create()

        val prefs = rig.field<PreferenceConfiguration>("prefConfig")
        assertThat(ReflectionHelpers.getField<Any>(rig.renderer, "prefs")).isSameInstanceAs(prefs)
    }

    @Test
    fun `the renderer gets the OpenGL renderer name that the GL preferences hold`() {
        // A name that no decoder rule looks at: MediaCodecHelper reads the first one it gets in a
        // sandbox and keeps it, so a name with a rule would make the other tests depend on this
        // one.
        rig.application
            .getSharedPreferences("GlPreferences", Context.MODE_PRIVATE)
            .edit()
            .putString("Renderer", "Test GPU")
            .commit()

        rig.create()

        assertThat(ReflectionHelpers.getField<String>(rig.renderer, "glRenderer"))
            .isEqualTo("Test GPU")
    }

    @Test
    fun `the menu chord of the gamepad opens the stream menu`() {
        assertThat(streaming().panel).isEqualTo(Panel.NONE)

        val chord =
            ReflectionHelpers.getField<MenuChordListener>(controllerHandler(), "menuChordListener")
        chord.onMenuChord()

        assertThat((rig.phase as StreamPhase.Streaming).panel).isEqualTo(Panel.MENU)
    }

    @Test
    fun `the keys of a gamepad on the USB driver have a sink in the chrome`() {
        rig.create()

        val sink = ReflectionHelpers.getField<ChromeKeySink?>(controllerHandler(), "chromeKeySink")

        assertThat(sink).isNotNull()
    }

    @Test
    fun `the decoder reports its numbers to the session`() {
        rig.create()
        val snapshot = PerfSnapshot(59.9f, 60f, 59.8f, 0.5f, 3, 1, null, 4.5f, 20f)

        ReflectionHelpers.getField<PerfSnapshotSink>(rig.renderer, "perfSink").onPerf(snapshot)

        assertThat(rig.session.perf.value).isEqualTo(snapshot)
    }

    @Test
    fun `the decoder counts its numbers only while the overlay or the menu shows them`() {
        streaming()
        assertThat(ReflectionHelpers.getField<Boolean>(rig.renderer, "perfEnabled")).isFalse()

        rig.session.openMenu()
        assertThat(ReflectionHelpers.getField<Boolean>(rig.renderer, "perfEnabled")).isTrue()

        rig.session.closePanel()
        assertThat(ReflectionHelpers.getField<Boolean>(rig.renderer, "perfEnabled")).isFalse()
    }

    @Test
    fun `opening the menu shows the gamepad mouse mode that the pad has`() {
        // The session starts with the setting (on); no pad has the mouse mode on.
        assertThat(streaming().gamepadMouse).isTrue()

        rig.session.openMenu()

        assertThat((rig.phase as StreamPhase.Streaming).gamepadMouse).isFalse()
    }

    @Test
    fun `the USB driver service is bound when the setting is on`() {
        rig.preference("checkbox_usb_driver", true)
        val service = rig.usbDriverService()

        rig.create()

        assertThat(shadowOf(rig.application).boundServiceConnections).hasSize(1)
        assertThat(rig.field<Boolean>("connectedToUsbDriverService")).isTrue()
        assertThat(rig.field<Any?>("usbDriverBinder")).isNotNull()
        assertThat(ReflectionHelpers.getField<Any?>(service, "listener"))
            .isSameInstanceAs(controllerHandler())
        assertThat(ReflectionHelpers.getField<Any?>(service, "stateListener"))
            .isSameInstanceAs(rig.game)
        assertThat(ReflectionHelpers.getField<Boolean>(service, "started")).isTrue()
    }

    @Test
    fun `the USB driver service is not bound when the setting is off`() {
        rig.create()

        assertThat(shadowOf(rig.application).boundServiceConnections).isEmpty()
        assertThat(rig.field<Boolean>("connectedToUsbDriverService")).isFalse()
    }

    @Test
    fun `the USB driver service is unbound when the activity is destroyed`() {
        rig.preference("checkbox_usb_driver", true)
        rig.usbDriverService()
        rig.create()
        val connection = shadowOf(rig.application).boundServiceConnections.single()

        rig.destroyActivity()

        // The controller handler unbinds its own SHIELD connection too.
        assertThat(shadowOf(rig.application).unboundServiceConnections).contains(connection)
    }
}
