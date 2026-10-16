package io.github.magalz.borealis.stream.input

/**
 * The button bits of a controller packet, as `com.limelight.nvstream.input.ControllerPacket`
 * defines them. The app module checks that the two agree.
 */
object GamepadButtons {
    const val DPAD_UP = 0x0001
    const val DPAD_DOWN = 0x0002
    const val DPAD_LEFT = 0x0004
    const val DPAD_RIGHT = 0x0008
    const val START = 0x0010
    const val SELECT = 0x0020
    const val RIGHT_BUMPER = 0x0200
    const val GUIDE = 0x0400
    const val A = 0x1000
    const val B = 0x2000
    const val Y = 0x8000
}
