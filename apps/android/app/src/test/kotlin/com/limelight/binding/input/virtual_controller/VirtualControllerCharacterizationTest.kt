// The package name is the inherited one: ADR 0059 keeps it, and it has an underscore.
@file:Suppress("PackageNaming")

package com.limelight.binding.input.virtual_controller

import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket
import io.github.magalz.borealis.input.Packet
import io.github.magalz.borealis.settings.ShadowMoonBridge
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * The on-screen controller as the stream screen uses it: it builds and refreshes the layout, shows
 * and hides it, takes the settings button's clicks through the configuration modes, and hands what
 * the controls press to the controller handler, again shortly after, in case the PC lost it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowMoonBridge::class])
class VirtualControllerCharacterizationTest {
    private val rig = OscRig()
    private val controller = rig.controller

    private fun context() = controller.controllerInputContext

    private fun click() = rig.configureButton.performClick()

    // The default layout

    @Test
    fun `a new controller is active, holds nothing and has put nothing on the screen`() {
        assertThat(controller.controllerMode).isEqualTo(VirtualController.ControllerMode.Active)
        assertThat(controller.elements).isEmpty()
        assertThat(rig.frame.childCount).isEqualTo(0)
        assertThat(rig.packets).isEmpty()
    }

    @Test
    fun `the modes are active, then move, then resize`() {
        assertThat(enumValues<VirtualController.ControllerMode>().map { it.name })
            .containsExactly("Active", "MoveButtons", "ResizeButtons")
            .inOrder()
    }

    @Test
    fun `the input context starts with nothing pressed and is one object`() {
        val context = controller.controllerInputContext

        assertThat(context).isSameInstanceAs(controller.controllerInputContext)
        assertThat(context.inputMap).isEqualTo(0)
        assertThat(context.leftTrigger).isEqualTo(0)
        assertThat(context.rightTrigger).isEqualTo(0)
        assertThat(context.leftStickX).isEqualTo(0)
        assertThat(context.leftStickY).isEqualTo(0)
        assertThat(context.rightStickX).isEqualTo(0)
        assertThat(context.rightStickY).isEqualTo(0)
    }

    @Test
    fun `the elements are the live list of the controller`() {
        controller.refreshLayout()

        assertThat(controller.elements).isSameInstanceAs(controller.elements)
        assertThat(controller.elements).hasSize(14)
    }

    // refreshLayout

    @Test
    fun `refreshing builds the default layout behind a settings button`() {
        rig.settings()

        controller.refreshLayout()

        assertThat(rig.frame.childCount).isEqualTo(15)
        assertThat(rig.frame.getChildAt(0)).isInstanceOf(Button::class.java)
        assertThat(rig.layout()).containsExactlyElementsIn(GoldenLayouts.of(OscRig.PHONE, false, true)).inOrder()
        controller.elements.forEachIndexed { index, element ->
            assertThat(rig.frame.getChildAt(index + 1)).isSameInstanceAs(element)
        }
    }

    @Test
    fun `the settings button is a faint, unfocusable square of 6 percent of the screen height at 15 15`() {
        controller.refreshLayout()

        val button = rig.configureButton
        val params = button.layoutParams as FrameLayout.LayoutParams
        assertThat(params.width).isEqualTo(64) // (int) (1080 * 0.06f) = 64
        assertThat(params.height).isEqualTo(64)
        assertThat(params.leftMargin).isEqualTo(15)
        assertThat(params.topMargin).isEqualTo(15)
        assertThat(button.alpha).isEqualTo(0.25f)
        assertThat(button.isFocusable).isFalse()
        assertThat(button.background).isNotNull()
    }

    @Test
    fun `the settings button follows the screen height and truncates`() {
        rig.screen(OscRig.TABLET_4_3)
        controller.refreshLayout()
        assertThat(rig.configureButton.layoutParams.width).isEqualTo(92) // 1536 * 0.06f = 92.16

        rig.screen(OscRig.TABLET_16_10)
        controller.refreshLayout()
        assertThat(rig.configureButton.layoutParams.width).isEqualTo(96) // 1600 * 0.06f = 96.0 in float
    }

    @Test
    fun `refreshing again replaces the layout and does not pile elements up`() {
        controller.refreshLayout()
        val before = controller.elements.toList()

        controller.refreshLayout()

        assertThat(rig.frame.childCount).isEqualTo(15)
        assertThat(controller.elements).hasSize(14)
        before.forEach { assertThat(it.parent).isNull() }
        controller.elements.forEach { assertThat(before).doesNotContain(it) }
    }

    @Test
    fun `refreshing builds the layout the settings ask for now`() {
        controller.refreshLayout()

        rig.settings(onlyL3R3 = true, showGuideButton = false)
        controller.refreshLayout()

        assertThat(rig.layout()).containsExactlyElementsIn(GoldenLayouts.of(OscRig.PHONE, true, false)).inOrder()
        assertThat(rig.frame.childCount).isEqualTo(3)
    }

    @Test
    fun `refreshing follows the screen it was turned to`() {
        controller.refreshLayout()

        rig.screen(OscRig.TV)
        controller.refreshLayout()

        assertThat(rig.layout()).containsExactlyElementsIn(GoldenLayouts.of(OscRig.TV, false, true)).inOrder()
    }

    @Test
    fun `refreshing does not forget which mode the controller is in`() {
        controller.refreshLayout()
        click()

        controller.refreshLayout()

        assertThat(controller.controllerMode).isEqualTo(VirtualController.ControllerMode.MoveButtons)
    }

    // removeElements, addElement

    @Test
    fun `removing the elements takes them and the settings button off the screen`() {
        controller.refreshLayout()
        val elements = controller.elements.toList()
        val button = rig.configureButton

        controller.removeElements()

        assertThat(controller.elements).isEmpty()
        assertThat(rig.frame.childCount).isEqualTo(0)
        elements.forEach { assertThat(it.parent).isNull() }
        assertThat(button.parent).isNull()
    }

    @Test
    fun `removing the elements of a controller that built nothing does nothing`() {
        controller.removeElements()
        controller.removeElements()

        assertThat(controller.elements).isEmpty()
        assertThat(rig.frame.childCount).isEqualTo(0)
    }

    @Test
    fun `an added element goes into the list and the frame at its place, with no margin on the right or the bottom`() {
        val element = DigitalButton(controller, 99, 1, rig.activity)

        controller.addElement(element, 7, 9, 30, 40)

        assertThat(controller.elements).containsExactly(element)
        assertThat(rig.frame.getChildAt(0)).isSameInstanceAs(element)
        val params = element.layoutParams as FrameLayout.LayoutParams
        assertThat(listOf(params.leftMargin, params.topMargin, params.width, params.height))
            .containsExactly(7, 9, 30, 40)
            .inOrder()
        assertThat(params.rightMargin).isEqualTo(0)
        assertThat(params.bottomMargin).isEqualTo(0)
    }

    // show and hide

    @Test
    fun `hiding makes every element and the settings button invisible, and showing makes them visible`() {
        controller.refreshLayout()

        controller.hide()
        assertThat(controller.elements.map { it.visibility }.distinct()).containsExactly(View.INVISIBLE)
        assertThat(rig.configureButton.visibility).isEqualTo(View.INVISIBLE)

        controller.show()
        assertThat(controller.elements.map { it.visibility }.distinct()).containsExactly(View.VISIBLE)
        assertThat(rig.configureButton.visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun `a layout that is built after hiding is visible again`() {
        controller.refreshLayout()
        controller.hide()

        controller.refreshLayout()

        assertThat(controller.elements.map { it.visibility }.distinct()).containsExactly(View.VISIBLE)
        assertThat(rig.configureButton.visibility).isEqualTo(View.INVISIBLE)
    }

    @Test
    fun `hiding and showing a controller with no layout does not fail`() {
        controller.hide()
        controller.show()

        assertThat(controller.elements).isEmpty()
    }

    // setOpacity

    @Test
    fun `the opacity is the share of 255 that the element takes into the top byte of its colours`() {
        controller.refreshLayout()

        controller.setOpacity(50) // 50 * 255 / 100 = 127

        controller.elements.forEach {
            assertThat(it.defaultColor).isEqualTo(0x7F888888)
            assertThat(it.pressedColor).isEqualTo(0x7F0000FF)
        }
    }

    @Test
    fun `the opacity can be set again without losing the colours under it`() {
        controller.refreshLayout()

        controller.setOpacity(10)
        controller.setOpacity(100)

        controller.elements.forEach {
            assertThat(it.defaultColor).isEqualTo(0xFF888888.toInt())
            assertThat(it.pressedColor).isEqualTo(0xFF0000FF.toInt())
        }
    }

    @Test
    fun `the opacity does not touch the settings button`() {
        controller.refreshLayout()

        controller.setOpacity(10)

        assertThat(rig.configureButton.alpha).isEqualTo(0.25f)
    }

    // The settings button

    @Test
    fun `the first click enters the move mode, says so and does not save the layout`() {
        controller.refreshLayout()

        click()

        assertThat(controller.controllerMode).isEqualTo(VirtualController.ControllerMode.MoveButtons)
        assertThat(ShadowToast.shownToastCount()).isEqualTo(1)
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Entering configuration mode (Move buttons)")
        assertThat(ShadowToast.getLatestToast().duration).isEqualTo(Toast.LENGTH_SHORT)
        assertThat(rig.saved.all).isEmpty()
    }

    @Test
    fun `the second click enters the resize mode and says so`() {
        controller.refreshLayout()

        click()
        click()

        assertThat(controller.controllerMode).isEqualTo(VirtualController.ControllerMode.ResizeButtons)
        assertThat(ShadowToast.shownToastCount()).isEqualTo(2)
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Entering configuration mode (Resize buttons)")
        assertThat(rig.saved.all).isEmpty()
    }

    @Test
    fun `the third click leaves the configuration mode, says so and saves the layout`() {
        controller.refreshLayout()

        click()
        click()
        click()

        assertThat(controller.controllerMode).isEqualTo(VirtualController.ControllerMode.Active)
        assertThat(ShadowToast.shownToastCount()).isEqualTo(3)
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Exiting configuration mode")
        assertThat(rig.savedLayout()).containsExactlyEntriesIn(
            GoldenLayouts.of(OscRig.PHONE, false, true).associateBy { it.id.toString() }
        )
    }

    @Test
    fun `the modes go round and round`() {
        controller.refreshLayout()

        click()
        click()
        click()
        click()

        assertThat(controller.controllerMode).isEqualTo(VirtualController.ControllerMode.MoveButtons)
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Entering configuration mode (Move buttons)")
    }

    @Test
    fun `what the user moved while configuring is what the third click saves`() {
        controller.refreshLayout()
        click()
        rig.element(6).loadConfiguration(JSONObject("""{"LEFT":5,"TOP":6,"WIDTH":70,"HEIGHT":80}"""))
        click()

        click()

        assertThat(rig.savedLayout().getValue("6")).isEqualTo(Placed(6, 5, 6, 70, 80))
    }

    @Test
    fun `a click redraws the settings button and every element`() {
        controller.refreshLayout()
        val views = controller.elements + rig.configureButton
        views.forEach { shadowOf(it).clearWasInvalidated() }

        click()

        views.forEach { assertThat(shadowOf(it).wasInvalidated()).isTrue() }
    }

    @Test
    fun `the elements are drawn red in the move mode, magenta in the resize mode and grey again after`() {
        controller.refreshLayout()
        val colours = { controller.elements.map { it.defaultColor }.distinct() }

        click()
        assertThat(colours()).containsExactly(0xF0FF0000.toInt())
        click()
        assertThat(colours()).containsExactly(0xF0FF00FF.toInt())
        click()
        assertThat(colours()).containsExactly(0xE5888888.toInt())
    }

    @Test
    fun `in a configuration mode the controls send nothing`() {
        controller.refreshLayout()
        click()

        rig.press(rig.element(VirtualControllerElement.EID_A))
        rig.release(rig.element(VirtualControllerElement.EID_A))

        assertThat(context().inputMap).isEqualTo(0)
        assertThat(rig.packets).isEmpty()
    }

    // Sending the controls' state

    @Test
    fun `sending hands the whole state of the controls to the handler, which sends it to the PC`() {
        val context = context()
        context.inputMap = (ControllerPacket.A_FLAG or ControllerPacket.LB_FLAG).toShort()
        context.leftStickX = 0x1111
        context.leftStickY = -0x2222
        context.rightStickX = 0x3333
        context.rightStickY = -0x4444
        context.leftTrigger = 0x55
        context.rightTrigger = 0xFF.toByte()

        controller.sendControllerInputContext()

        assertThat(rig.packets)
            .containsExactly(
                Packet(
                    controller = 0,
                    buttons = ControllerPacket.A_FLAG or ControllerPacket.LB_FLAG,
                    leftTrigger = 0x55,
                    rightTrigger = -1,
                    leftStickX = 0x1111,
                    leftStickY = -0x2222,
                    rightStickX = 0x3333,
                    rightStickY = -0x4444,
                )
            )
    }

    @Test
    fun `the sticks, the triggers and the buttons do not trade places on the way to the handler`() {
        val context = context()
        context.leftStickX = 1
        context.leftStickY = 2
        context.rightStickX = 3
        context.rightStickY = 4
        context.leftTrigger = 5
        context.rightTrigger = 6

        controller.sendControllerInputContext()

        val sent = rig.packets.single()
        assertThat(
                listOf(
                    sent.leftStickX,
                    sent.leftStickY,
                    sent.rightStickX,
                    sent.rightStickY,
                    sent.leftTrigger,
                    sent.rightTrigger,
                )
            )
            .containsExactly(1, 2, 3, 4, 5, 6)
            .inOrder()
    }

    @Test
    fun `a button above the sign bit of the short reaches the handler with the sign extended`() {
        context().inputMap = ControllerPacket.Y_FLAG.toShort()

        controller.sendControllerInputContext()

        // 0x8000 as a short is -32768, which the int parameter of the handler takes as 0xFFFF8000
        assertThat(rig.packets.single().buttons).isEqualTo(-0x8000)
    }

    @Test
    fun `the state is sent at once and again 25, 50 and 75 milliseconds later`() {
        context().inputMap = ControllerPacket.A_FLAG.toShort()

        controller.sendControllerInputContext()
        assertThat(rig.packets).hasSize(1)

        rig.advanceBy(24)
        assertThat(rig.packets).hasSize(1)
        rig.advanceBy(1)
        assertThat(rig.packets).hasSize(2)
        rig.advanceBy(24)
        assertThat(rig.packets).hasSize(2)
        rig.advanceBy(1)
        assertThat(rig.packets).hasSize(3)
        rig.advanceBy(24)
        assertThat(rig.packets).hasSize(3)
        rig.advanceBy(1)
        assertThat(rig.packets).hasSize(4)

        rig.advanceBy(10_000)
        assertThat(rig.packets).hasSize(4)
        assertThat(rig.packets.map { it.buttons }.distinct()).containsExactly(ControllerPacket.A_FLAG)
    }

    @Test
    fun `a new input cancels the retransmits of the one before`() {
        context().inputMap = ControllerPacket.A_FLAG.toShort()
        controller.sendControllerInputContext()
        rig.advanceBy(30) // the first retransmit has gone out
        assertThat(rig.packets).hasSize(2)

        controller.sendControllerInputContext()
        assertThat(rig.packets).hasSize(3)
        rig.advanceBy(10_000)

        // the new input has its own three, and the 50 and 75 of the first are gone
        assertThat(rig.packets).hasSize(6)
    }

    @Test
    fun `the retransmits send the state of the moment, not the state that was sent`() {
        context().inputMap = ControllerPacket.A_FLAG.toShort()
        controller.sendControllerInputContext()

        // the stick of a pad is zeroed and that packet is lost; nothing was sent for it
        context().inputMap = 0

        rig.advanceBy(25)
        assertThat(rig.packets.map { it.buttons }).containsExactly(ControllerPacket.A_FLAG, 0).inOrder()
    }

    @Test
    fun `the retransmits run on the main looper whichever thread built the controller`() {
        lateinit var built: VirtualController
        Thread { built = VirtualController(rig.handlerRig.handler, rig.frame, rig.activity) }
            .apply {
                start()
                join()
            }
        built.controllerInputContext.inputMap = ControllerPacket.A_FLAG.toShort()

        built.sendControllerInputContext()
        rig.advanceBy(75)

        assertThat(rig.packets).hasSize(4)
    }

    @Test
    fun `a controller without a handler sends nothing and does not fail`() {
        val bare = OscRig(withHandler = false)
        bare.controller.controllerInputContext.inputMap = ControllerPacket.A_FLAG.toShort()

        bare.controller.sendControllerInputContext()
        bare.advanceBy(10_000)

        assertThat(bare.packets).isEmpty()
    }
}
