package com.limelight.binding.input

import android.annotation.SuppressLint
import android.hardware.input.InputManager
import android.os.Build
import android.util.SparseArray
import android.view.InputDevice
import android.view.KeyEvent
import androidx.annotation.RequiresApi

/**
 * Class to translate a Android key code into the codes GFE is expecting
 *
 * @author Diego Waxemberg
 * @author Cameron Gutman
 */
class KeyboardTranslator : InputManager.InputDeviceListener {
    private class KeyboardMapping
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    constructor(device: InputDevice) {
        private val deviceKeyCodeToQwertyKeyCode: IntArray

        init {
            val maxKeyCode = KeyEvent.getMaxKeyCode()

            deviceKeyCodeToQwertyKeyCode = IntArray(maxKeyCode + 1)

            // Any unmatched keycodes are treated as unknown
            deviceKeyCodeToQwertyKeyCode.fill(KeyEvent.KEYCODE_UNKNOWN)

            for (i in 0..maxKeyCode) {
                val deviceKeyCode = device.getKeyCodeForKeyLocation(i)
                // A broken device can answer a key code outside the platform's range.
                if (deviceKeyCode != KeyEvent.KEYCODE_UNKNOWN && deviceKeyCode in 0..maxKeyCode) {
                    deviceKeyCodeToQwertyKeyCode[deviceKeyCode] = i
                }
            }
        }

        fun getQwertyKeyCodeForDeviceKeyCode(deviceKeyCode: Int): Int {
            if (deviceKeyCode < 0 || deviceKeyCode > KeyEvent.getMaxKeyCode()) {
                return KeyEvent.KEYCODE_UNKNOWN
            }

            return deviceKeyCodeToQwertyKeyCode[deviceKeyCode]
        }
    }

    private val keyboardMappings = SparseArray<KeyboardMapping>()

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            for (deviceId in InputDevice.getDeviceIds()) {
                val device = InputDevice.getDevice(deviceId)
                if (device != null && device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC) {
                    keyboardMappings.set(deviceId, KeyboardMapping(device))
                }
            }
        }
    }

    fun hasNormalizedMapping(keycode: Int, deviceId: Int): Boolean {
        if (deviceId >= 0) {
            val mapping = keyboardMappings.get(deviceId)
            if (mapping != null) {
                // Try to map this device-specific keycode onto a QWERTY layout.
                // GFE assumes incoming keycodes are from a QWERTY keyboard.
                val qwertyKeyCode = mapping.getQwertyKeyCodeForDeviceKeyCode(keycode)
                if (qwertyKeyCode != KeyEvent.KEYCODE_UNKNOWN) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Translates the given keycode and returns the GFE keycode
     *
     * @param keycode the code to be translated
     * @param deviceId InputDevice.getId() or -1 if unknown
     * @return a GFE keycode for the given keycode
     */
    fun translate(keycode: Int, deviceId: Int): Short {
        var code = keycode

        // If a device ID was provided, look up the keyboard mapping
        if (deviceId >= 0) {
            val mapping = keyboardMappings.get(deviceId)
            if (mapping != null) {
                // Try to map this device-specific keycode onto a QWERTY layout.
                // GFE assumes incoming keycodes are from a QWERTY keyboard.
                val qwertyKeyCode = mapping.getQwertyKeyCodeForDeviceKeyCode(code)
                if (qwertyKeyCode != KeyEvent.KEYCODE_UNKNOWN) {
                    code = qwertyKeyCode
                }
            }
        }

        val translated = VIRTUAL_KEYS.getOrElse(code) { 0 }
        if (translated == 0) {
            return 0
        }

        return ((KEY_PREFIX.toInt() shl BITS_PER_BYTE) or translated).toShort()
    }

    override fun onInputDeviceAdded(index: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val device = InputDevice.getDevice(index)
            if (device != null && device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC) {
                keyboardMappings.put(index, KeyboardMapping(device))
            }
        }
    }

    override fun onInputDeviceRemoved(index: Int) {
        keyboardMappings.remove(index)
    }

    override fun onInputDeviceChanged(index: Int) {
        keyboardMappings.remove(index)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val device = InputDevice.getDevice(index)
            if (device != null && device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC) {
                keyboardMappings.set(index, KeyboardMapping(device))
            }
        }
    }

    companion object {
        /** GFE's prefix for every key code */
        private const val KEY_PREFIX: Short = 0x80

        private const val BITS_PER_BYTE = 8

        const val VK_0 = 48
        const val VK_A = 65
        const val VK_NUMPAD0 = 96
        const val VK_CAPS_LOCK = 20
        const val VK_CLEAR = 12
        const val VK_BACK_SPACE = 8
        const val VK_ESCAPE = 27
        const val VK_F1 = 112
        const val VK_F13 = 0x7C
        const val VK_END = 35
        const val VK_HOME = 36
        const val VK_NUM_LOCK = 144
        const val VK_PAGE_UP = 33
        const val VK_PAGE_DOWN = 34
        const val VK_SCROLL_LOCK = 145
        const val VK_SPACE = 32
        const val VK_PRINTSCREEN = 154
        const val VK_TAB = 9
        const val VK_LEFT = 37
        const val VK_RIGHT = 39
        const val VK_UP = 38
        const val VK_DOWN = 40
        const val VK_BACK_QUOTE = 192
        const val VK_PAUSE = 19

        // The Windows virtual-key codes that translate() wrote as literals. The names are the ones
        // of the Windows documentation.
        private const val VK_RETURN = 0x0d
        private const val VK_PRINT = 0x2A
        private const val VK_SNAPSHOT = 0x2C
        private const val VK_INSERT = 0x2d
        private const val VK_DELETE = 0x2e
        private const val VK_LWIN = 0x5b
        private const val VK_RWIN = 0x5c
        private const val VK_APPS = 0x5d
        private const val VK_MULTIPLY = 0x6A
        private const val VK_ADD = 0x6B
        private const val VK_SUBTRACT = 0x6D
        private const val VK_DECIMAL = 0x6E
        private const val VK_DIVIDE = 0x6F
        private const val VK_LSHIFT = 0xA0
        private const val VK_RSHIFT = 0xA1
        private const val VK_LCONTROL = 0xA2
        private const val VK_RCONTROL = 0xA3
        private const val VK_LMENU = 0xA4
        private const val VK_RMENU = 0xA5
        private const val VK_OEM_1 = 0xba
        private const val VK_OEM_PLUS = 0xbb
        private const val VK_OEM_COMMA = 0xbc
        private const val VK_OEM_MINUS = 0xbd
        private const val VK_OEM_PERIOD = 0xbe
        private const val VK_OEM_2 = 0xbf
        private const val VK_OEM_4 = 0xdb
        private const val VK_OEM_5 = 0xdc
        private const val VK_OEM_6 = 0xdd
        private const val VK_OEM_7 = 0xde

        // This is a poor man's mapping between Android key codes
        // and Windows VK_* codes. For all defined VK_ codes, see:
        // https://msdn.microsoft.com/en-us/library/windows/desktop/dd375731(v=vs.85).aspx
        // It is indexed by Android key code, up to the highest of them (F24), and built once; a key
        // that is not in it has 0, which is no Windows virtual-key code.
        // Converted as is from the inherited Java; InlinedApi stays because some of these key
        // codes are newer than the minimum SDK (Sprint 18c backlog).
        @SuppressLint("InlinedApi")
        private val VIRTUAL_KEYS =
            IntArray(KeyEvent.KEYCODE_F24 + 1).apply {
                putRange(KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9, VK_0)
                putRange(KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z, VK_A)
                putRange(KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9, VK_NUMPAD0)
                putRange(KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12, VK_F1)
                putRange(KeyEvent.KEYCODE_F13..KeyEvent.KEYCODE_F24, VK_F13)
                this[KeyEvent.KEYCODE_ALT_LEFT] = VK_LMENU
                this[KeyEvent.KEYCODE_ALT_RIGHT] = VK_RMENU
                this[KeyEvent.KEYCODE_BACKSLASH] = VK_OEM_5
                this[KeyEvent.KEYCODE_CAPS_LOCK] = VK_CAPS_LOCK
                this[KeyEvent.KEYCODE_CLEAR] = VK_CLEAR
                this[KeyEvent.KEYCODE_COMMA] = VK_OEM_COMMA
                this[KeyEvent.KEYCODE_CTRL_LEFT] = VK_LCONTROL
                this[KeyEvent.KEYCODE_CTRL_RIGHT] = VK_RCONTROL
                this[KeyEvent.KEYCODE_DEL] = VK_BACK_SPACE
                this[KeyEvent.KEYCODE_ENTER] = VK_RETURN
                this[KeyEvent.KEYCODE_PLUS] = VK_OEM_PLUS
                this[KeyEvent.KEYCODE_EQUALS] = VK_OEM_PLUS
                this[KeyEvent.KEYCODE_ESCAPE] = VK_ESCAPE
                this[KeyEvent.KEYCODE_FORWARD_DEL] = VK_DELETE
                this[KeyEvent.KEYCODE_INSERT] = VK_INSERT
                this[KeyEvent.KEYCODE_LEFT_BRACKET] = VK_OEM_4
                this[KeyEvent.KEYCODE_META_LEFT] = VK_LWIN
                this[KeyEvent.KEYCODE_META_RIGHT] = VK_RWIN
                this[KeyEvent.KEYCODE_MENU] = VK_APPS
                this[KeyEvent.KEYCODE_MINUS] = VK_OEM_MINUS
                this[KeyEvent.KEYCODE_MOVE_END] = VK_END
                this[KeyEvent.KEYCODE_MOVE_HOME] = VK_HOME
                this[KeyEvent.KEYCODE_NUM_LOCK] = VK_NUM_LOCK
                this[KeyEvent.KEYCODE_PAGE_DOWN] = VK_PAGE_DOWN
                this[KeyEvent.KEYCODE_PAGE_UP] = VK_PAGE_UP
                this[KeyEvent.KEYCODE_PERIOD] = VK_OEM_PERIOD
                this[KeyEvent.KEYCODE_RIGHT_BRACKET] = VK_OEM_6
                this[KeyEvent.KEYCODE_SCROLL_LOCK] = VK_SCROLL_LOCK
                this[KeyEvent.KEYCODE_SEMICOLON] = VK_OEM_1
                this[KeyEvent.KEYCODE_SHIFT_LEFT] = VK_LSHIFT
                this[KeyEvent.KEYCODE_SHIFT_RIGHT] = VK_RSHIFT
                this[KeyEvent.KEYCODE_SLASH] = VK_OEM_2
                this[KeyEvent.KEYCODE_SPACE] = VK_SPACE
                // Android defines this as SysRq/PrntScrn
                this[KeyEvent.KEYCODE_SYSRQ] = VK_PRINTSCREEN
                this[KeyEvent.KEYCODE_TAB] = VK_TAB
                this[KeyEvent.KEYCODE_DPAD_LEFT] = VK_LEFT
                this[KeyEvent.KEYCODE_DPAD_RIGHT] = VK_RIGHT
                this[KeyEvent.KEYCODE_DPAD_UP] = VK_UP
                this[KeyEvent.KEYCODE_DPAD_DOWN] = VK_DOWN
                this[KeyEvent.KEYCODE_GRAVE] = VK_BACK_QUOTE
                this[KeyEvent.KEYCODE_APOSTROPHE] = VK_OEM_7
                this[KeyEvent.KEYCODE_BREAK] = VK_PAUSE
                this[KeyEvent.KEYCODE_NUMPAD_DIVIDE] = VK_DIVIDE
                this[KeyEvent.KEYCODE_NUMPAD_MULTIPLY] = VK_MULTIPLY
                this[KeyEvent.KEYCODE_NUMPAD_SUBTRACT] = VK_SUBTRACT
                this[KeyEvent.KEYCODE_NUMPAD_ADD] = VK_ADD
                this[KeyEvent.KEYCODE_NUMPAD_DOT] = VK_DECIMAL
                this[KeyEvent.KEYCODE_PRINT] = VK_PRINT
                this[KeyEvent.KEYCODE_SCREENSHOT] = VK_SNAPSHOT
            }

        /**
         * Maps the key codes of [keyCodes] to the virtual keys that count up from
         * [firstVirtualKey].
         */
        private fun IntArray.putRange(keyCodes: IntRange, firstVirtualKey: Int) {
            for (keyCode in keyCodes) {
                this[keyCode] = firstVirtualKey + (keyCode - keyCodes.first)
            }
        }
    }
}
