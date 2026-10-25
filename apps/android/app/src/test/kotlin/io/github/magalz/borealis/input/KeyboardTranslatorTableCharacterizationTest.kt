package io.github.magalz.borealis.input

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import com.limelight.binding.input.KeyboardTranslator
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The key codes that Android sends and the codes that the host gets: a Windows virtual-key code
 * behind the `0x80` prefix that GFE puts on every key, so the code is a negative `Short`. The table
 * holds what `KeyboardTranslator.translate` returned for every Android key code when it was
 * recorded (Sprint 13, on the Java). Every key code that is not in it returns 0, and 0 means "no
 * raw key": the stream sends such a key as text, if it has any. No device is given here, so no
 * keyboard layout is looked up (see `KeyboardTranslatorLayoutCharacterizationTest`).
 */
@RunWith(RobolectricTestRunner::class)
class KeyboardTranslatorTableCharacterizationTest {
    private val translator = KeyboardTranslator()

    private fun translate(keyCode: Int): Short = translator.translate(keyCode, NO_DEVICE)

    @Test
    fun `every key code in the table, and no other, has a code behind the prefix`() {
        val actual =
            (SWEEP_FROM..SWEEP_TO)
                .associateWith { translate(it) }
                .filterValues { it != NO_KEY }

        assertThat(actual).isEqualTo(TRANSLATED.mapValues { it.value.toShort() })
    }

    @Test
    fun `a key that has a code is a negative short, the prefix being the top byte`() {
        assertThat(translate(KeyEvent.KEYCODE_A).toInt()).isEqualTo(-32703)
        assertThat(translate(KeyEvent.KEYCODE_A).toInt() and 0xFF00).isEqualTo(0x8000)
        assertThat(translate(KeyEvent.KEYCODE_F24).toInt()).isEqualTo(-32633)
        assertThat(TRANSLATED.keys.all { translate(it) < 0 }).isTrue()
    }

    @Test
    fun `the digits, the letters, the numpad digits and the function keys count up from one base`() {
        for (offset in 0..9) {
            assertThat(translate(KeyEvent.KEYCODE_0 + offset)).isEqualTo((0x8030 + offset).toShort())
            assertThat(translate(KeyEvent.KEYCODE_NUMPAD_0 + offset))
                .isEqualTo((0x8060 + offset).toShort())
        }
        for (offset in 0..25) {
            assertThat(translate(KeyEvent.KEYCODE_A + offset)).isEqualTo((0x8041 + offset).toShort())
        }
        for (offset in 0..11) {
            assertThat(translate(KeyEvent.KEYCODE_F1 + offset)).isEqualTo((0x8070 + offset).toShort())
            assertThat(translate(KeyEvent.KEYCODE_F13 + offset))
                .isEqualTo((0x807C + offset).toShort())
        }
    }

    @Test
    fun `the keys next to a range are not translated as part of it`() {
        // Before and after the digits.
        assertThat(translate(KeyEvent.KEYCODE_0 - 1)).isEqualTo(NO_KEY)
        assertThat(translate(KeyEvent.KEYCODE_9 + 1)).isEqualTo(NO_KEY)
        // Before the letters is CLEAR, after them is COMMA.
        assertThat(translate(KeyEvent.KEYCODE_A - 1)).isEqualTo(0x800C.toShort())
        assertThat(translate(KeyEvent.KEYCODE_Z + 1)).isEqualTo(0x80BC.toShort())
        // Before the numpad digits is NUM_LOCK, after them is NUMPAD_DIVIDE.
        assertThat(translate(KeyEvent.KEYCODE_NUMPAD_0 - 1)).isEqualTo(0x8090.toShort())
        assertThat(translate(KeyEvent.KEYCODE_NUMPAD_9 + 1)).isEqualTo(0x806F.toShort())
        // Before F1 and F13, and after F24, nothing; after F12 is NUM_LOCK.
        assertThat(translate(KeyEvent.KEYCODE_F1 - 1)).isEqualTo(NO_KEY)
        assertThat(translate(KeyEvent.KEYCODE_F12 + 1)).isEqualTo(0x8090.toShort())
        assertThat(translate(KeyEvent.KEYCODE_F13 - 1)).isEqualTo(NO_KEY)
        assertThat(translate(KeyEvent.KEYCODE_F24 + 1)).isEqualTo(NO_KEY)
    }

    @Test
    fun `plus and equals send the same key, the left and the right modifiers send two`() {
        assertThat(translate(KeyEvent.KEYCODE_PLUS)).isEqualTo(translate(KeyEvent.KEYCODE_EQUALS))
        for ((left, right) in
            listOf(
                KeyEvent.KEYCODE_SHIFT_LEFT to KeyEvent.KEYCODE_SHIFT_RIGHT,
                KeyEvent.KEYCODE_CTRL_LEFT to KeyEvent.KEYCODE_CTRL_RIGHT,
                KeyEvent.KEYCODE_ALT_LEFT to KeyEvent.KEYCODE_ALT_RIGHT,
                KeyEvent.KEYCODE_META_LEFT to KeyEvent.KEYCODE_META_RIGHT,
            )) {
            assertThat(translate(right).toInt()).isEqualTo(translate(left) + 1)
        }
    }

    @Test
    fun `a key code without a Windows counterpart returns 0 whatever the device`() {
        for (keyCode in
            listOf(
                KeyEvent.KEYCODE_UNKNOWN,
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_HOME,
                KeyEvent.KEYCODE_VOLUME_UP,
                KeyEvent.KEYCODE_BUTTON_A,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_NUMPAD_COMMA,
                KeyEvent.KEYCODE_NUMPAD_EQUALS,
                KeyEvent.KEYCODE_AT,
                KeyEvent.KEYCODE_STAR,
                -1,
                Int.MAX_VALUE,
                Int.MIN_VALUE,
            )) {
            assertThat(translator.translate(keyCode, NO_DEVICE)).isEqualTo(NO_KEY)
            assertThat(translator.translate(keyCode, UNMAPPED_DEVICE)).isEqualTo(NO_KEY)
        }
    }

    @Test
    fun `a device id that has no layout is translated like no device`() {
        for (keyCode in TRANSLATED.keys) {
            assertThat(translator.translate(keyCode, UNMAPPED_DEVICE)).isEqualTo(translate(keyCode))
        }
    }

    private companion object {
        const val NO_DEVICE = -1
        const val UNMAPPED_DEVICE = 42
        const val NO_KEY: Short = 0

        /** The sweep covers the key codes of every API level and some beyond. */
        const val SWEEP_FROM = -3
        const val SWEEP_TO = 1100

        /**
         * Android key code to the 16 bits the host gets: the prefix `0x80` and the Windows
         * virtual-key code. Written out from what the Java returned; see the comment on the class.
         */
        val TRANSLATED: Map<Int, Int> =
            mapOf(
                KeyEvent.KEYCODE_0 to 0x8030,
                KeyEvent.KEYCODE_1 to 0x8031,
                KeyEvent.KEYCODE_2 to 0x8032,
                KeyEvent.KEYCODE_3 to 0x8033,
                KeyEvent.KEYCODE_4 to 0x8034,
                KeyEvent.KEYCODE_5 to 0x8035,
                KeyEvent.KEYCODE_6 to 0x8036,
                KeyEvent.KEYCODE_7 to 0x8037,
                KeyEvent.KEYCODE_8 to 0x8038,
                KeyEvent.KEYCODE_9 to 0x8039,
                KeyEvent.KEYCODE_DPAD_UP to 0x8026,
                KeyEvent.KEYCODE_DPAD_DOWN to 0x8028,
                KeyEvent.KEYCODE_DPAD_LEFT to 0x8025,
                KeyEvent.KEYCODE_DPAD_RIGHT to 0x8027,
                KeyEvent.KEYCODE_CLEAR to 0x800C,
                KeyEvent.KEYCODE_A to 0x8041,
                KeyEvent.KEYCODE_B to 0x8042,
                KeyEvent.KEYCODE_C to 0x8043,
                KeyEvent.KEYCODE_D to 0x8044,
                KeyEvent.KEYCODE_E to 0x8045,
                KeyEvent.KEYCODE_F to 0x8046,
                KeyEvent.KEYCODE_G to 0x8047,
                KeyEvent.KEYCODE_H to 0x8048,
                KeyEvent.KEYCODE_I to 0x8049,
                KeyEvent.KEYCODE_J to 0x804A,
                KeyEvent.KEYCODE_K to 0x804B,
                KeyEvent.KEYCODE_L to 0x804C,
                KeyEvent.KEYCODE_M to 0x804D,
                KeyEvent.KEYCODE_N to 0x804E,
                KeyEvent.KEYCODE_O to 0x804F,
                KeyEvent.KEYCODE_P to 0x8050,
                KeyEvent.KEYCODE_Q to 0x8051,
                KeyEvent.KEYCODE_R to 0x8052,
                KeyEvent.KEYCODE_S to 0x8053,
                KeyEvent.KEYCODE_T to 0x8054,
                KeyEvent.KEYCODE_U to 0x8055,
                KeyEvent.KEYCODE_V to 0x8056,
                KeyEvent.KEYCODE_W to 0x8057,
                KeyEvent.KEYCODE_X to 0x8058,
                KeyEvent.KEYCODE_Y to 0x8059,
                KeyEvent.KEYCODE_Z to 0x805A,
                KeyEvent.KEYCODE_COMMA to 0x80BC,
                KeyEvent.KEYCODE_PERIOD to 0x80BE,
                KeyEvent.KEYCODE_ALT_LEFT to 0x80A4,
                KeyEvent.KEYCODE_ALT_RIGHT to 0x80A5,
                KeyEvent.KEYCODE_SHIFT_LEFT to 0x80A0,
                KeyEvent.KEYCODE_SHIFT_RIGHT to 0x80A1,
                KeyEvent.KEYCODE_TAB to 0x8009,
                KeyEvent.KEYCODE_SPACE to 0x8020,
                KeyEvent.KEYCODE_ENTER to 0x800D,
                KeyEvent.KEYCODE_DEL to 0x8008,
                KeyEvent.KEYCODE_GRAVE to 0x80C0,
                KeyEvent.KEYCODE_MINUS to 0x80BD,
                KeyEvent.KEYCODE_EQUALS to 0x80BB,
                KeyEvent.KEYCODE_LEFT_BRACKET to 0x80DB,
                KeyEvent.KEYCODE_RIGHT_BRACKET to 0x80DD,
                KeyEvent.KEYCODE_BACKSLASH to 0x80DC,
                KeyEvent.KEYCODE_SEMICOLON to 0x80BA,
                KeyEvent.KEYCODE_APOSTROPHE to 0x80DE,
                KeyEvent.KEYCODE_SLASH to 0x80BF,
                KeyEvent.KEYCODE_PLUS to 0x80BB,
                KeyEvent.KEYCODE_MENU to 0x805D,
                KeyEvent.KEYCODE_PAGE_UP to 0x8021,
                KeyEvent.KEYCODE_PAGE_DOWN to 0x8022,
                KeyEvent.KEYCODE_ESCAPE to 0x801B,
                KeyEvent.KEYCODE_FORWARD_DEL to 0x802E,
                KeyEvent.KEYCODE_CTRL_LEFT to 0x80A2,
                KeyEvent.KEYCODE_CTRL_RIGHT to 0x80A3,
                KeyEvent.KEYCODE_CAPS_LOCK to 0x8014,
                KeyEvent.KEYCODE_SCROLL_LOCK to 0x8091,
                KeyEvent.KEYCODE_META_LEFT to 0x805B,
                KeyEvent.KEYCODE_META_RIGHT to 0x805C,
                KeyEvent.KEYCODE_SYSRQ to 0x809A,
                KeyEvent.KEYCODE_BREAK to 0x8013,
                KeyEvent.KEYCODE_MOVE_HOME to 0x8024,
                KeyEvent.KEYCODE_MOVE_END to 0x8023,
                KeyEvent.KEYCODE_INSERT to 0x802D,
                KeyEvent.KEYCODE_F1 to 0x8070,
                KeyEvent.KEYCODE_F2 to 0x8071,
                KeyEvent.KEYCODE_F3 to 0x8072,
                KeyEvent.KEYCODE_F4 to 0x8073,
                KeyEvent.KEYCODE_F5 to 0x8074,
                KeyEvent.KEYCODE_F6 to 0x8075,
                KeyEvent.KEYCODE_F7 to 0x8076,
                KeyEvent.KEYCODE_F8 to 0x8077,
                KeyEvent.KEYCODE_F9 to 0x8078,
                KeyEvent.KEYCODE_F10 to 0x8079,
                KeyEvent.KEYCODE_F11 to 0x807A,
                KeyEvent.KEYCODE_F12 to 0x807B,
                KeyEvent.KEYCODE_NUM_LOCK to 0x8090,
                KeyEvent.KEYCODE_NUMPAD_0 to 0x8060,
                KeyEvent.KEYCODE_NUMPAD_1 to 0x8061,
                KeyEvent.KEYCODE_NUMPAD_2 to 0x8062,
                KeyEvent.KEYCODE_NUMPAD_3 to 0x8063,
                KeyEvent.KEYCODE_NUMPAD_4 to 0x8064,
                KeyEvent.KEYCODE_NUMPAD_5 to 0x8065,
                KeyEvent.KEYCODE_NUMPAD_6 to 0x8066,
                KeyEvent.KEYCODE_NUMPAD_7 to 0x8067,
                KeyEvent.KEYCODE_NUMPAD_8 to 0x8068,
                KeyEvent.KEYCODE_NUMPAD_9 to 0x8069,
                KeyEvent.KEYCODE_NUMPAD_DIVIDE to 0x806F,
                KeyEvent.KEYCODE_NUMPAD_MULTIPLY to 0x806A,
                KeyEvent.KEYCODE_NUMPAD_SUBTRACT to 0x806D,
                KeyEvent.KEYCODE_NUMPAD_ADD to 0x806B,
                KeyEvent.KEYCODE_NUMPAD_DOT to 0x806E,
                KeyEvent.KEYCODE_SCREENSHOT to 0x802C,
                KeyEvent.KEYCODE_PRINT to 0x802A,
                KeyEvent.KEYCODE_F13 to 0x807C,
                KeyEvent.KEYCODE_F14 to 0x807D,
                KeyEvent.KEYCODE_F15 to 0x807E,
                KeyEvent.KEYCODE_F16 to 0x807F,
                KeyEvent.KEYCODE_F17 to 0x8080,
                KeyEvent.KEYCODE_F18 to 0x8081,
                KeyEvent.KEYCODE_F19 to 0x8082,
                KeyEvent.KEYCODE_F20 to 0x8083,
                KeyEvent.KEYCODE_F21 to 0x8084,
                KeyEvent.KEYCODE_F22 to 0x8085,
                KeyEvent.KEYCODE_F23 to 0x8086,
                KeyEvent.KEYCODE_F24 to 0x8087,
            )
    }
}
