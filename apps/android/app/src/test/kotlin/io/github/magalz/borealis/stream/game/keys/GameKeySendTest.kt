package io.github.magalz.borealis.stream.game.keys

import android.view.KeyCharacterMap
import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.nvstream.input.KeyboardPacket
import io.github.magalz.borealis.stream.game.GameTestBase
import org.junit.Test

/**
 * What the stream activity sends to the host for the keys of a hardware keyboard while the stream
 * has the input: the key and its modifiers, the keys that no layout knows (sent as text when they
 * make a character), and the keys that it eats.
 */
internal class GameKeySendTest : GameTestBase() {
    private val down = KeyboardPacket.KEY_DOWN
    private val up = KeyboardPacket.KEY_UP

    /**
     * A key, the host key that it is sent as, and the modifier that the key holds while it is down.
     */
    private data class Key(val name: String, val code: Int, val host: Int, val modifier: Byte = 0)

    private val keys =
        listOf(
            Key("A", KeyEvent.KEYCODE_A, 0x8041),
            Key("Z", KeyEvent.KEYCODE_Z, 0x805A),
            Key("5", KeyEvent.KEYCODE_5, 0x8035),
            Key("F1", KeyEvent.KEYCODE_F1, 0x8070),
            Key("Enter", KeyEvent.KEYCODE_ENTER, 0x800D),
            Key("Escape", KeyEvent.KEYCODE_ESCAPE, 0x801B),
            Key("Up", KeyEvent.KEYCODE_DPAD_UP, 0x8026),
            Key("Space", KeyEvent.KEYCODE_SPACE, 0x8020),
            Key("Left Shift", KeyEvent.KEYCODE_SHIFT_LEFT, 0x80A0, KeyboardPacket.MODIFIER_SHIFT),
            Key("Right Shift", KeyEvent.KEYCODE_SHIFT_RIGHT, 0x80A1, KeyboardPacket.MODIFIER_SHIFT),
            Key("Left Ctrl", KeyEvent.KEYCODE_CTRL_LEFT, 0x80A2, KeyboardPacket.MODIFIER_CTRL),
            Key("Right Ctrl", KeyEvent.KEYCODE_CTRL_RIGHT, 0x80A3, KeyboardPacket.MODIFIER_CTRL),
            Key("Left Alt", KeyEvent.KEYCODE_ALT_LEFT, 0x80A4, KeyboardPacket.MODIFIER_ALT),
            Key("Right Alt", KeyEvent.KEYCODE_ALT_RIGHT, 0x80A5, KeyboardPacket.MODIFIER_ALT),
            Key("Left Meta", KeyEvent.KEYCODE_META_LEFT, 0x805B, KeyboardPacket.MODIFIER_META),
            Key("Right Meta", KeyEvent.KEYCODE_META_RIGHT, 0x805C, KeyboardPacket.MODIFIER_META),
        )

    @Test
    fun `a key goes to the host as a virtual key when it goes down and when it comes up`() {
        rig.streaming()

        for (key in keys) {
            forgetCalls()

            val handled = rig.tap(key.code)

            assertWithMessage(key.name).that(handled).containsExactly(true, true)
            // A modifier key holds itself while it is down, and lets itself go when it comes up.
            assertWithMessage(key.name)
                .that(sentKeys())
                .containsExactly(
                    SentKey(key.host, down, key.modifier, NON_NORMALIZED),
                    SentKey(key.host, up, 0, NON_NORMALIZED),
                )
                .inOrder()
        }
    }

    @Test
    fun `a key that comes from a layout of its own is sent as the QWERTY key at its place, normalized`() {
        rig.streaming()
        // The key that sends Q sits where A sits on a QWERTY keyboard, as on an AZERTY keyboard.
        rig.keyboardLayout(KEYBOARD_ID, KeyEvent.KEYCODE_Q to KeyEvent.KEYCODE_A)
        forgetCalls()

        val handled = rig.tap(KeyEvent.KEYCODE_Q) + rig.tap(KeyEvent.KEYCODE_B)

        assertThat(handled).containsExactly(true, true, true, true)
        assertThat(sentKeys())
            .containsExactly(
                // The layout places this key: the host gets the QWERTY key and no flag.
                SentKey(0x8041, down, 0, 0),
                SentKey(0x8041, up, 0, 0),
                // The layout does not place this one: the flag says so.
                SentKey(0x8042, down, 0, NON_NORMALIZED),
                SentKey(0x8042, up, 0, NON_NORMALIZED),
            )
            .inOrder()
    }

    @Test
    fun `a key that repeats is eaten, and the host sees it down once`() {
        rig.streaming()
        forgetCalls()

        val first = rig.game.dispatchKeyEvent(keyboardKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A))
        val repeats =
            (1..3).map {
                rig.game.dispatchKeyEvent(
                    keyboardKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, repeat = it)
                )
            }

        assertThat(first).isTrue()
        assertThat(repeats).containsExactly(true, true, true)
        assertThat(sentKeys()).containsExactly(SentKey(0x8041, down, 0, NON_NORMALIZED))
    }

    @Test
    fun `the modifiers that the event carries go with the key`() {
        rig.streaming()
        // What a software keyboard does: the key has Shift on it with no key event for Shift.
        val shiftCtrl =
            (KeyboardPacket.MODIFIER_SHIFT.toInt() or KeyboardPacket.MODIFIER_CTRL.toInt())
        val metas =
            listOf(
                KeyEvent.META_SHIFT_ON to KeyboardPacket.MODIFIER_SHIFT,
                KeyEvent.META_CTRL_ON to KeyboardPacket.MODIFIER_CTRL,
                KeyEvent.META_ALT_ON to KeyboardPacket.MODIFIER_ALT,
                KeyEvent.META_META_ON to KeyboardPacket.MODIFIER_META,
                (KeyEvent.META_SHIFT_ON or KeyEvent.META_CTRL_ON) to shiftCtrl.toByte(),
            )

        for ((meta, modifier) in metas) {
            forgetCalls()

            rig.tap(KeyEvent.KEYCODE_A, meta)

            assertWithMessage("meta state $meta")
                .that(sentKeys())
                .containsExactly(
                    SentKey(0x8041, down, modifier, NON_NORMALIZED),
                    SentKey(0x8041, up, modifier, NON_NORMALIZED),
                )
                .inOrder()
        }
    }

    @Test
    fun `the modifiers that are held down join the ones that the event carries`() {
        rig.streaming()
        rig.down(KeyEvent.KEYCODE_SHIFT_LEFT)
        rig.down(KeyEvent.KEYCODE_CTRL_RIGHT)
        forgetCalls()

        rig.tap(KeyEvent.KEYCODE_A)
        rig.tap(KeyEvent.KEYCODE_B, KeyEvent.META_ALT_ON)
        rig.up(KeyEvent.KEYCODE_CTRL_RIGHT)
        rig.tap(KeyEvent.KEYCODE_C, KeyEvent.META_META_ON)

        val shiftCtrl = 0x03.toByte()
        val shiftCtrlAlt = 0x07.toByte()
        val shift = KeyboardPacket.MODIFIER_SHIFT
        val shiftMeta = 0x09.toByte()
        assertThat(sentKeys())
            .containsExactly(
                SentKey(0x8041, down, shiftCtrl, NON_NORMALIZED),
                SentKey(0x8041, up, shiftCtrl, NON_NORMALIZED),
                SentKey(0x8042, down, shiftCtrlAlt, NON_NORMALIZED),
                SentKey(0x8042, up, shiftCtrlAlt, NON_NORMALIZED),
                // Letting a modifier go takes it out of the byte, in the key up of the modifier.
                SentKey(0x80A3, up, shift, NON_NORMALIZED),
                SentKey(0x8043, down, shiftMeta, NON_NORMALIZED),
                SentKey(0x8043, up, shiftMeta, NON_NORMALIZED),
            )
            .inOrder()
    }

    @Test
    fun `the plus key goes with Shift, because the host has one key for equals and plus`() {
        rig.streaming()
        forgetCalls()

        rig.tap(KeyEvent.KEYCODE_PLUS)
        rig.tap(KeyEvent.KEYCODE_EQUALS)
        rig.tap(KeyEvent.KEYCODE_PLUS, KeyEvent.META_CTRL_ON)

        val shift = KeyboardPacket.MODIFIER_SHIFT
        val shiftCtrl = 0x03.toByte()
        assertThat(sentKeys())
            .containsExactly(
                SentKey(0x80BB, down, shift, NON_NORMALIZED),
                SentKey(0x80BB, up, shift, NON_NORMALIZED),
                SentKey(0x80BB, down, 0, NON_NORMALIZED),
                SentKey(0x80BB, up, 0, NON_NORMALIZED),
                SentKey(0x80BB, down, shiftCtrl, NON_NORMALIZED),
                SentKey(0x80BB, up, shiftCtrl, NON_NORMALIZED),
            )
            .inOrder()
    }

    @Test
    fun `a key with no virtual key but with a character goes to the host as text, down only`() {
        rig.streaming()
        forgetCalls()

        // @, # and * have no virtual key of their own on the host.
        val handled =
            listOf(KeyEvent.KEYCODE_AT, KeyEvent.KEYCODE_POUND, KeyEvent.KEYCODE_STAR).flatMap {
                rig.tap(it)
            }

        // The key up of a key that was sent as text is handled too, and sends nothing.
        assertThat(handled).containsExactly(true, true, true, true, true, true)
        assertThat(sentTexts()).containsExactly("@", "#", "*").inOrder()
        assertThat(sentKeys()).isEmpty()
    }

    @Test
    fun `a character is sent again for each repeat, because the host does not repeat text`() {
        rig.streaming()
        forgetCalls()

        val handled =
            (0..2).map {
                rig.game.dispatchKeyEvent(
                    keyboardKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_AT, repeat = it)
                )
            }

        assertThat(handled).containsExactly(true, true, true)
        assertThat(sentTexts()).containsExactly("@", "@", "@")
    }

    @Test
    fun `a character is sent as UTF-8 text, and one outside the basic plane loses its high bits`() {
        rig.streaming()
        forgetCalls()

        for (character in listOf('é'.code, 0x20AC, 0x1F600)) {
            val event =
                keyboardKey(KeySpec(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_AT, unicode = character))
            assertThat(rig.game.dispatchKeyEvent(event)).isTrue()
        }

        // The Java narrows the code point to a char: U+1F600 arrives as U+F600.
        assertThat(sentTexts()).containsExactly("é", "€", Char(0xF600).toString()).inOrder()
    }

    @Test
    fun `a key with no virtual key and no character is not handled, up or down`() {
        rig.streaming()
        forgetCalls()

        // The volume key and a key that the layout does not know.
        val handled =
            listOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_NUMPAD_COMMA).flatMap {
                rig.tap(it)
            }

        assertThat(handled).containsExactly(false, false, false, false)
        assertThat(sentCalls()).isEmpty()
    }

    @Test
    fun `a dead character is not sent, down or up, because the host cannot compose it`() {
        rig.streaming()
        forgetCalls()
        // The accent bit of the character says that it waits for the next key.
        val dead = KeyCharacterMap.COMBINING_ACCENT or '´'.code
        val spec = KeySpec(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_AT, unicode = dead)

        val pressed = rig.game.handleKeyDown(keyboardKey(spec))
        val released = rig.game.handleKeyUp(keyboardKey(spec.copy(action = KeyEvent.ACTION_UP)))

        assertThat(pressed).isFalse()
        assertThat(released).isFalse()
        assertThat(sentCalls()).isEmpty()
    }

    @Test
    fun `a key up with a character that was not sent is handled only when it has one`() {
        rig.streaming()
        forgetCalls()

        val withCharacter =
            rig.game.handleKeyUp(keyboardKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_AT))
        val withoutCharacter =
            rig.game.handleKeyUp(keyboardKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP))

        assertThat(withCharacter).isTrue()
        assertThat(withoutCharacter).isFalse()
        assertThat(sentCalls()).isEmpty()
    }

    @Test
    @Suppress("DEPRECATION") // onKeyMultiple and ACTION_MULTIPLE are what the activity handles.
    fun `the keys of a software keyboard that make no key event arrive as text`() {
        rig.streaming()
        forgetCalls()

        val handled =
            listOf("héllo", "日本語", "").map {
                rig.game.onKeyMultiple(KeyEvent.KEYCODE_UNKNOWN, 1, textKey(it))
            }

        assertThat(handled).containsExactly(true, true, true)
        assertThat(sentTexts()).containsExactly("héllo", "日本語", "").inOrder()
    }

    @Test
    @Suppress("DEPRECATION") // onKeyMultiple and ACTION_MULTIPLE are what the activity handles.
    fun `a multiple key with no characters, or with a key code of its own, is not text`() {
        rig.streaming()
        forgetCalls()
        val noCharacters = keyEvent(KeySpec(KeyEvent.ACTION_MULTIPLE, KeyEvent.KEYCODE_UNKNOWN))
        val withKeyCode =
            keyEvent(KeySpec(KeyEvent.ACTION_MULTIPLE, KeyEvent.KEYCODE_A, repeat = 2))

        val handled =
            listOf(noCharacters, withKeyCode).map { rig.game.onKeyMultiple(it.keyCode, 1, it) }

        assertThat(handled).containsExactly(false, false)
        assertThat(sentCalls()).isEmpty()
    }
}
