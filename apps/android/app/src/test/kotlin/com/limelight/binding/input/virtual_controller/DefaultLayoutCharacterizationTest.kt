package com.limelight.binding.input.virtual_controller

import android.widget.FrameLayout
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket
import io.github.magalz.borealis.settings.ShadowMoonBridge
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The layout the on-screen controller starts from, before the user's saved changes: which controls
 * there are, what they are, where they sit and how big they are on each kind of screen, what they
 * are labelled and which buttons they press, and how opaque they are.
 *
 * These pin what the Java loader did when the tests were written, so that the conversion to Kotlin
 * and the work after it keep the layout users have on their screens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowMoonBridge::class])
class DefaultLayoutCharacterizationTest {
    private fun defaultLayout(
        screen: Screen,
        onlyL3R3: Boolean = false,
        flipFaceButtons: Boolean = false,
        showGuideButton: Boolean = true,
        opacity: Int? = null,
    ): OscRig {
        val rig = OscRig(screen)
        rig.settings(onlyL3R3, flipFaceButtons, showGuideButton, opacity)
        VirtualControllerConfigurationLoader.createDefaultLayout(rig.controller, rig.activity)
        return rig
    }

    private fun checkPlacement(screen: Screen, onlyL3R3: Boolean, showGuideButton: Boolean) {
        val rig = defaultLayout(screen, onlyL3R3 = onlyL3R3, showGuideButton = showGuideButton)

        assertThat(rig.layout())
            .containsExactlyElementsIn(GoldenLayouts.of(screen, onlyL3R3, showGuideButton))
            .inOrder()
    }

    @Test
    fun `a phone in landscape gets the full layout with Guide`() =
        checkPlacement(OscRig.PHONE, onlyL3R3 = false, showGuideButton = true)

    @Test
    fun `a phone in landscape gets the full layout without Guide`() =
        checkPlacement(OscRig.PHONE, onlyL3R3 = false, showGuideButton = false)

    @Test
    fun `a phone in landscape gets L3 and R3 with Guide`() =
        checkPlacement(OscRig.PHONE, onlyL3R3 = true, showGuideButton = true)

    @Test
    fun `a phone in landscape gets L3 and R3 without Guide`() =
        checkPlacement(OscRig.PHONE, onlyL3R3 = true, showGuideButton = false)

    @Test
    fun `a TV at 1920 by 1080 gets the full layout with Guide`() =
        checkPlacement(OscRig.TV, onlyL3R3 = false, showGuideButton = true)

    @Test
    fun `a TV at 1920 by 1080 gets the full layout without Guide`() =
        checkPlacement(OscRig.TV, onlyL3R3 = false, showGuideButton = false)

    @Test
    fun `a TV at 1920 by 1080 gets L3 and R3 with Guide`() =
        checkPlacement(OscRig.TV, onlyL3R3 = true, showGuideButton = true)

    @Test
    fun `a TV at 1920 by 1080 gets L3 and R3 without Guide`() =
        checkPlacement(OscRig.TV, onlyL3R3 = true, showGuideButton = false)

    @Test
    fun `a 4 to 3 tablet gets the full layout with Guide, with the right side moved to the left`() =
        checkPlacement(OscRig.TABLET_4_3, onlyL3R3 = false, showGuideButton = true)

    @Test
    fun `a 4 to 3 tablet gets the full layout without Guide`() =
        checkPlacement(OscRig.TABLET_4_3, onlyL3R3 = false, showGuideButton = false)

    @Test
    fun `a 4 to 3 tablet gets L3 and R3 with Guide`() =
        checkPlacement(OscRig.TABLET_4_3, onlyL3R3 = true, showGuideButton = true)

    @Test
    fun `a 4 to 3 tablet gets L3 and R3 without Guide`() =
        checkPlacement(OscRig.TABLET_4_3, onlyL3R3 = true, showGuideButton = false)

    @Test
    fun `a 16 to 10 tablet, whose height is not a multiple of the grid rows, gets the full layout with Guide`() =
        checkPlacement(OscRig.TABLET_16_10, onlyL3R3 = false, showGuideButton = true)

    @Test
    fun `a 16 to 10 tablet gets the full layout without Guide`() =
        checkPlacement(OscRig.TABLET_16_10, onlyL3R3 = false, showGuideButton = false)

    @Test
    fun `a 16 to 10 tablet gets L3 and R3 with Guide`() =
        checkPlacement(OscRig.TABLET_16_10, onlyL3R3 = true, showGuideButton = true)

    @Test
    fun `a 16 to 10 tablet gets L3 and R3 without Guide`() =
        checkPlacement(OscRig.TABLET_16_10, onlyL3R3 = true, showGuideButton = false)

    @Test
    fun `a 1920 by 1200 panel, where the float order of the grid shows, gets the full layout with Guide`() =
        checkPlacement(OscRig.WUXGA, onlyL3R3 = false, showGuideButton = true)

    @Test
    fun `a 1920 by 1200 panel gets the full layout without Guide`() =
        checkPlacement(OscRig.WUXGA, onlyL3R3 = false, showGuideButton = false)

    @Test
    fun `a 1920 by 1200 panel gets L3 and R3 with Guide`() =
        checkPlacement(OscRig.WUXGA, onlyL3R3 = true, showGuideButton = true)

    @Test
    fun `a 1920 by 1200 panel gets L3 and R3 without Guide`() =
        checkPlacement(OscRig.WUXGA, onlyL3R3 = true, showGuideButton = false)

    @Test
    fun `the layout is the elements in the frame, in order, with no margin on the right or the bottom`() {
        val rig = defaultLayout(OscRig.PHONE)

        assertThat(rig.frame.childCount).isEqualTo(rig.controller.elements.size)
        rig.controller.elements.forEachIndexed { index, element ->
            assertThat(rig.frame.getChildAt(index)).isSameInstanceAs(element)
            val params = element.layoutParams as FrameLayout.LayoutParams
            assertThat(params.rightMargin).isEqualTo(0)
            assertThat(params.bottomMargin).isEqualTo(0)
        }
        assertThat(rig.controller.elements.map { it.eid })
            .containsExactlyElementsIn(GoldenLayouts.FULL_ORDER + GoldenLayouts.GUIDE)
            .inOrder()
    }

    @Test
    fun `the layout with only L3 and R3 has those two and Guide in order`() {
        val rig = defaultLayout(OscRig.PHONE, onlyL3R3 = true)

        assertThat(rig.controller.elements.map { it.eid })
            .containsExactlyElementsIn(GoldenLayouts.L3_R3_ORDER + GoldenLayouts.GUIDE)
            .inOrder()
    }

    @Test
    fun `flipping the face buttons moves nothing`() {
        val flipped = defaultLayout(OscRig.PHONE, flipFaceButtons = true)

        assertThat(flipped.layout()).containsExactlyElementsIn(GoldenLayouts.of(OscRig.PHONE, false, true)).inOrder()
    }

    @Test
    fun `each element is what its id says`() {
        val rig = defaultLayout(OscRig.PHONE)
        val classes = rig.controller.elements.associate { it.eid to it.javaClass }

        assertThat(classes)
            .containsExactlyEntriesIn(
                mapOf(
                    VirtualControllerElement.EID_DPAD to DigitalPad::class.java,
                    VirtualControllerElement.EID_LT to LeftTrigger::class.java,
                    VirtualControllerElement.EID_RT to RightTrigger::class.java,
                    VirtualControllerElement.EID_LB to DigitalButton::class.java,
                    VirtualControllerElement.EID_RB to DigitalButton::class.java,
                    VirtualControllerElement.EID_A to DigitalButton::class.java,
                    VirtualControllerElement.EID_B to DigitalButton::class.java,
                    VirtualControllerElement.EID_X to DigitalButton::class.java,
                    VirtualControllerElement.EID_Y to DigitalButton::class.java,
                    VirtualControllerElement.EID_BACK to DigitalButton::class.java,
                    VirtualControllerElement.EID_START to DigitalButton::class.java,
                    VirtualControllerElement.EID_LS to LeftAnalogStick::class.java,
                    VirtualControllerElement.EID_RS to RightAnalogStick::class.java,
                    VirtualControllerElement.EID_GDB to DigitalButton::class.java,
                )
            )
    }

    @Test
    fun `the layout with only L3 and R3 is made of buttons`() {
        val rig = defaultLayout(OscRig.PHONE, onlyL3R3 = true)

        assertThat(rig.controller.elements.map { it.javaClass }.distinct()).containsExactly(DigitalButton::class.java)
    }

    @Test
    fun `the buttons are labelled and have no icon`() {
        val rig = defaultLayout(OscRig.PHONE)

        val labels = rig.controller.elements.filterIsInstance<DigitalButton>().associate { it.eid to it.label() }
        assertThat(labels)
            .containsExactlyEntriesIn(
                mapOf(
                    VirtualControllerElement.EID_A to "A",
                    VirtualControllerElement.EID_B to "B",
                    VirtualControllerElement.EID_X to "X",
                    VirtualControllerElement.EID_Y to "Y",
                    VirtualControllerElement.EID_LT to "LT",
                    VirtualControllerElement.EID_RT to "RT",
                    VirtualControllerElement.EID_LB to "LB",
                    VirtualControllerElement.EID_RB to "RB",
                    VirtualControllerElement.EID_BACK to "BACK",
                    VirtualControllerElement.EID_START to "START",
                    VirtualControllerElement.EID_GDB to "GUIDE",
                )
            )
        assertThat(rig.controller.elements.filterIsInstance<DigitalButton>().map { it.icon() }.distinct())
            .containsExactly(-1)
    }

    @Test
    fun `the layout with only L3 and R3 labels them`() {
        val rig = defaultLayout(OscRig.PHONE, onlyL3R3 = true)

        val labels = rig.controller.elements.filterIsInstance<DigitalButton>().associate { it.eid to it.label() }
        assertThat(labels)
            .containsExactly(
                VirtualControllerElement.EID_LSB, "L3",
                VirtualControllerElement.EID_RSB, "R3",
                VirtualControllerElement.EID_GDB, "GUIDE",
            )
    }

    @Test
    fun `flipping the face buttons swaps the labels of A and B and of X and Y`() {
        val rig = defaultLayout(OscRig.PHONE, flipFaceButtons = true)

        val labels = rig.controller.elements.filterIsInstance<DigitalButton>().associate { it.eid to it.label() }
        assertThat(labels)
            .containsAtLeast(
                VirtualControllerElement.EID_A, "B",
                VirtualControllerElement.EID_B, "A",
                VirtualControllerElement.EID_X, "Y",
                VirtualControllerElement.EID_Y, "X",
            )
        assertThat(labels).containsEntry(VirtualControllerElement.EID_LB, "LB")
        assertThat(labels).containsEntry(VirtualControllerElement.EID_START, "START")
    }

    @Test
    fun `each button presses the flag of its name`() {
        val rig = defaultLayout(OscRig.PHONE)
        val flags =
            mapOf(
                VirtualControllerElement.EID_A to ControllerPacket.A_FLAG,
                VirtualControllerElement.EID_B to ControllerPacket.B_FLAG,
                VirtualControllerElement.EID_X to ControllerPacket.X_FLAG,
                VirtualControllerElement.EID_Y to ControllerPacket.Y_FLAG,
                VirtualControllerElement.EID_LB to ControllerPacket.LB_FLAG,
                VirtualControllerElement.EID_RB to ControllerPacket.RB_FLAG,
                VirtualControllerElement.EID_BACK to ControllerPacket.BACK_FLAG,
                VirtualControllerElement.EID_START to ControllerPacket.PLAY_FLAG,
                VirtualControllerElement.EID_GDB to ControllerPacket.SPECIAL_BUTTON_FLAG,
            )

        flags.forEach { (id, flag) ->
            rig.press(rig.element(id))
            assertThat(rig.controller.controllerInputContext.inputMap).isEqualTo(flag.toShort())
            rig.release(rig.element(id))
            assertThat(rig.controller.controllerInputContext.inputMap).isEqualTo(0)
        }
    }

    @Test
    fun `L3 and R3 press the stick clicks`() {
        val rig = defaultLayout(OscRig.PHONE, onlyL3R3 = true)

        rig.press(rig.element(VirtualControllerElement.EID_LSB))
        assertThat(rig.controller.controllerInputContext.inputMap).isEqualTo(ControllerPacket.LS_CLK_FLAG.toShort())
        rig.release(rig.element(VirtualControllerElement.EID_LSB))
        rig.press(rig.element(VirtualControllerElement.EID_RSB))
        assertThat(rig.controller.controllerInputContext.inputMap).isEqualTo(ControllerPacket.RS_CLK_FLAG.toShort())
    }

    @Test
    fun `flipping the face buttons makes A press B and X press Y, and the other way round`() {
        val rig = defaultLayout(OscRig.PHONE, flipFaceButtons = true)
        val flags =
            mapOf(
                VirtualControllerElement.EID_A to ControllerPacket.B_FLAG,
                VirtualControllerElement.EID_B to ControllerPacket.A_FLAG,
                VirtualControllerElement.EID_X to ControllerPacket.Y_FLAG,
                VirtualControllerElement.EID_Y to ControllerPacket.X_FLAG,
            )

        flags.forEach { (id, flag) ->
            rig.press(rig.element(id))
            assertThat(rig.controller.controllerInputContext.inputMap).isEqualTo(flag.toShort())
            rig.release(rig.element(id))
        }
    }

    @Test
    fun `the triggers press fully and the sticks have nothing pressed`() {
        val rig = defaultLayout(OscRig.PHONE)

        rig.press(rig.element(VirtualControllerElement.EID_LT))
        assertThat(rig.controller.controllerInputContext.leftTrigger).isEqualTo(0xFF.toByte())
        rig.release(rig.element(VirtualControllerElement.EID_LT))
        assertThat(rig.controller.controllerInputContext.leftTrigger).isEqualTo(0)
        rig.press(rig.element(VirtualControllerElement.EID_RT))
        assertThat(rig.controller.controllerInputContext.rightTrigger).isEqualTo(0xFF.toByte())
        rig.release(rig.element(VirtualControllerElement.EID_RT))
        assertThat(rig.controller.controllerInputContext.rightTrigger).isEqualTo(0)
    }

    @Test
    fun `the opacity of the settings is given to every element, 90 percent without a setting`() {
        val rig = defaultLayout(OscRig.PHONE)

        // 90 * 255 / 100 = 229 = 0xE5
        rig.controller.elements.forEach {
            assertThat(it.defaultColor).isEqualTo(0xE5888888.toInt())
            assertThat(it.pressedColor).isEqualTo(0xE50000FF.toInt())
        }
    }

    @Test
    fun `the opacity setting colours the elements with its share of 255`() {
        val rig = defaultLayout(OscRig.TV, opacity = 40)

        // 40 * 255 / 100 = 102 = 0x66
        rig.controller.elements.forEach {
            assertThat(it.defaultColor).isEqualTo(0x66888888)
            assertThat(it.pressedColor).isEqualTo(0x660000FF)
        }
    }

    @Test
    fun `an opacity of 100 is fully opaque and an opacity of 0 is fully transparent`() {
        val opaque = defaultLayout(OscRig.TV, opacity = 100)
        val transparent = defaultLayout(OscRig.TV, opacity = 0)

        opaque.controller.elements.forEach { assertThat(it.defaultColor).isEqualTo(0xFF888888.toInt()) }
        transparent.controller.elements.forEach { assertThat(it.defaultColor).isEqualTo(0x00888888) }
    }
}
