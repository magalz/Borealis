package io.github.magalz.borealis.stream

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.StreamConfiguration
import com.limelight.nvstream.input.ControllerPacket
import com.limelight.nvstream.input.KeyboardPacket
import com.limelight.nvstream.input.MouseButtonPacket
import java.lang.reflect.Modifier
import org.junit.Test

/**
 * The constants of the stream and packet types, as the host, the JNI layer and the inherited Java
 * (`case` labels, `Map.entry` values) read them. Each constant keeps its name, its primitive type
 * (a `byte` stays a `byte`: it goes into a packet field of that width) and its value.
 *
 * Characterization: these tests pass on the Java classes and on their Kotlin conversion.
 */
class StreamConstantsCharacterizationTest {
    private class Constant(val type: Class<*>, val value: Any)

    private val int: Class<*> = Integer.TYPE
    private val byte: Class<*> = java.lang.Byte.TYPE

    /** Every public static primitive field of [type], by name: its type and its value. */
    private fun constantsOf(type: Class<*>): Map<String, Constant> =
        type.fields
            .filter { Modifier.isStatic(it.modifiers) && it.type.isPrimitive }
            .onEach { assertThat(Modifier.isFinal(it.modifiers)).isTrue() }
            .associate { it.name to Constant(it.type, it.get(null)!!) }

    private fun assertConstants(type: Class<*>, expected: Map<String, Constant>) {
        val actual = constantsOf(type)

        assertThat(actual.keys).containsExactlyElementsIn(expected.keys)
        for ((name, constant) in expected) {
            assertThat(actual.getValue(name).type).isEqualTo(constant.type)
            assertThat(actual.getValue(name).value).isEqualTo(constant.value)
        }
    }

    private fun ints(vararg entries: Pair<String, Int>): Map<String, Constant> =
        entries.associate { (name, value) ->
            name to Constant(int, value)
        }

    private fun bytes(vararg entries: Pair<String, Int>): Map<String, Constant> =
        entries.associate { (name, value) ->
            name to Constant(byte, value.toByte())
        }

    @Test
    fun `the controller button flags keep their values`() {
        assertConstants(
            ControllerPacket::class.java,
            ints(
                "A_FLAG" to 0x1000,
                "B_FLAG" to 0x2000,
                "X_FLAG" to 0x4000,
                "Y_FLAG" to 0x8000,
                "UP_FLAG" to 0x0001,
                "DOWN_FLAG" to 0x0002,
                "LEFT_FLAG" to 0x0004,
                "RIGHT_FLAG" to 0x0008,
                "LB_FLAG" to 0x0100,
                "RB_FLAG" to 0x0200,
                "PLAY_FLAG" to 0x0010,
                "BACK_FLAG" to 0x0020,
                "LS_CLK_FLAG" to 0x0040,
                "RS_CLK_FLAG" to 0x0080,
                "SPECIAL_BUTTON_FLAG" to 0x0400,
                "PADDLE1_FLAG" to 0x010000,
                "PADDLE2_FLAG" to 0x020000,
                "PADDLE3_FLAG" to 0x040000,
                "PADDLE4_FLAG" to 0x080000,
                "TOUCHPAD_FLAG" to 0x100000,
                "MISC_FLAG" to 0x200000,
            ),
        )
    }

    @Test
    fun `the keyboard packet codes and modifiers keep their values`() {
        assertConstants(
            KeyboardPacket::class.java,
            bytes(
                "KEY_DOWN" to 0x03,
                "KEY_UP" to 0x04,
                "MODIFIER_SHIFT" to 0x01,
                "MODIFIER_CTRL" to 0x02,
                "MODIFIER_ALT" to 0x04,
                "MODIFIER_META" to 0x08,
            ),
        )
    }

    @Test
    fun `the mouse button packet codes keep their values`() {
        assertConstants(
            MouseButtonPacket::class.java,
            bytes(
                "PRESS_EVENT" to 0x07,
                "RELEASE_EVENT" to 0x08,
                "BUTTON_LEFT" to 0x01,
                "BUTTON_MIDDLE" to 0x02,
                "BUTTON_RIGHT" to 0x03,
                "BUTTON_X1" to 0x04,
                "BUTTON_X2" to 0x05,
            ),
        )
    }

    @Test
    fun `the stream configuration constants keep their values`() {
        assertConstants(
            StreamConfiguration::class.java,
            ints(
                "INVALID_APP_ID" to 0,
                "STREAM_CFG_LOCAL" to 0,
                "STREAM_CFG_REMOTE" to 1,
                "STREAM_CFG_AUTO" to 2,
            ),
        )
    }
}
