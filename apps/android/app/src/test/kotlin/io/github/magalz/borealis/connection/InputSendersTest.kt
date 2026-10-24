package io.github.magalz.borealis.connection

import android.app.ActivityManager
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.input.MouseButtonPacket
import com.limelight.nvstream.jni.MoonBridge
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What each input sender of `NvConnection` hands to `MoonBridge`: which call, with which arguments
 * in which order, and what comes back. `Game`, `ControllerHandler` and the touch contexts call
 * these from Java.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    shadows =
        [
            ShadowRecordingBridge::class,
            ShadowMonkeyActivityManager::class,
            ShadowLoadedNativeLibrary::class,
            ShadowHostRules::class,
        ]
)
class InputSendersTest {
    @get:Rule val rig = ConnectionRig()

    private val connection: NvConnection by lazy { rig.connection(config = null) }

    private fun sent(): BridgeCall = ShadowRecordingBridge.calls.single()

    private fun assertSent(name: String, vararg args: Any?) {
        assertThat(sent().name).isEqualTo(name)
        assertThat(sent().args).containsExactlyElementsIn(args).inOrder()
    }

    @Test
    fun `a mouse move sends the two deltas`() {
        connection.sendMouseMove(11, -22)

        assertSent("sendMouseMove", 11.toShort(), (-22).toShort())
    }

    @Test
    fun `a mouse position sends the point, then the size of the view`() {
        connection.sendMousePosition(100, 200, 1920, 1080)

        assertSent(
            "sendMousePosition",
            100.toShort(),
            200.toShort(),
            1920.toShort(),
            1080.toShort(),
        )
    }

    @Test
    fun `a mouse move as a position sends the deltas, then the size of the view`() {
        connection.sendMouseMoveAsMousePosition(5, -6, 1280, 720)

        assertSent(
            "sendMouseMoveAsMousePosition",
            5.toShort(),
            (-6).toShort(),
            1280.toShort(),
            720.toShort(),
        )
    }

    @Test
    fun `a button press and a release are the press event and the release event of the button`() {
        connection.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT)
        connection.sendMouseButtonUp(MouseButtonPacket.BUTTON_X1)

        assertThat(ShadowRecordingBridge.calls.map { it.name to it.args })
            .containsExactly(
                "sendMouseButton" to
                    listOf(MouseButtonPacket.PRESS_EVENT, MouseButtonPacket.BUTTON_RIGHT),
                "sendMouseButton" to
                    listOf(MouseButtonPacket.RELEASE_EVENT, MouseButtonPacket.BUTTON_X1),
            )
            .inOrder()
    }

    @Test
    fun `controller input sends the pad, the mask, the buttons, the triggers and the four stick axes`() {
        connection.sendControllerInput(
            2,
            0x0F,
            0x1234,
            10,
            -20,
            1000,
            -1000,
            2000,
            -2000,
        )

        assertSent(
            "sendMultiControllerInput",
            2.toShort(),
            0x0F.toShort(),
            0x1234,
            10.toByte(),
            (-20).toByte(),
            1000.toShort(),
            (-1000).toShort(),
            2000.toShort(),
            (-2000).toShort(),
        )
    }

    @Test
    fun `a key sends the key map, the direction, the modifiers and the flags`() {
        connection.sendKeyboardInput(0x8041.toShort(), 3, 0x02, 1)

        assertSent("sendKeyboardInput", 0x8041.toShort(), 3.toByte(), 0x02.toByte(), 1.toByte())
    }

    @Test
    fun `a scroll in clicks is 120 high-resolution units per click, vertical or horizontal`() {
        val clicks = listOf<Byte>(0, 1, -1, 2, 100, -100)

        for (click in clicks) {
            connection.sendMouseScroll(click)
            connection.sendMouseHScroll(click)
        }

        val expected = clicks.flatMap {
            listOf(
                "sendMouseHighResScroll" to (it * 120).toShort(),
                "sendMouseHighResHScroll" to (it * 120).toShort(),
            )
        }
        assertThat(ShadowRecordingBridge.calls.map { it.name to it.args.single() })
            .containsExactlyElementsIn(expected)
            .inOrder()
    }

    /** A click is a `Byte`, so 127 clicks are 15240 units and -128 are -15360: no `Short` wrap. */
    @Test
    fun `the extremes of a scroll in clicks are not narrowed`() {
        connection.sendMouseScroll(Byte.MAX_VALUE)
        connection.sendMouseHScroll(Byte.MIN_VALUE)

        assertThat(ShadowRecordingBridge.calls.map { it.name to it.args.single() })
            .containsExactly(
                "sendMouseHighResScroll" to 15_240.toShort(),
                "sendMouseHighResHScroll" to (-15_360).toShort(),
            )
            .inOrder()
    }

    @Test
    fun `a high-resolution scroll is sent as it is, vertical or horizontal`() {
        connection.sendMouseHighResScroll(Short.MAX_VALUE)
        connection.sendMouseHighResHScroll(Short.MIN_VALUE)

        assertThat(ShadowRecordingBridge.calls.map { it.name to it.args.single() })
            .containsExactly(
                "sendMouseHighResScroll" to Short.MAX_VALUE,
                "sendMouseHighResHScroll" to Short.MIN_VALUE,
            )
            .inOrder()
    }

    @Test
    fun `a touch event sends its fields in order and returns the answer of the library`() {
        ShadowRecordingBridge.intResult = 7

        val result = connection.sendTouchEvent(1, 4, 0.25f, 0.75f, 0.5f, 0.125f, 0.0625f, 90)

        assertThat(result).isEqualTo(7)
        assertSent(
            "sendTouchEvent",
            1.toByte(),
            4,
            0.25f,
            0.75f,
            0.5f,
            0.125f,
            0.0625f,
            90.toShort(),
        )
    }

    @Test
    fun `a pen event sends its fields in order and returns the answer of the library`() {
        ShadowRecordingBridge.intResult = 9

        val result = connection.sendPenEvent(3, 1, 2, 0.25f, 0.75f, 0.5f, 0.125f, 0.0625f, 45, 30)

        assertThat(result).isEqualTo(9)
        assertSent(
            "sendPenEvent",
            3.toByte(),
            1.toByte(),
            2.toByte(),
            0.25f,
            0.75f,
            0.5f,
            0.125f,
            0.0625f,
            45.toShort(),
            30.toByte(),
        )
    }

    @Test
    fun `a controller arrival sends its fields in order and returns the answer of the library`() {
        ShadowRecordingBridge.intResult = 3

        val result = connection.sendControllerArrivalEvent(1, 0x03, 2, 0x4000, 0x0FF)

        assertThat(result).isEqualTo(3)
        assertSent(
            "sendControllerArrivalEvent",
            1.toByte(),
            0x03.toShort(),
            2.toByte(),
            0x4000,
            0x0FF.toShort(),
        )
    }

    @Test
    fun `a controller touch sends its fields in order and returns the answer of the library`() {
        ShadowRecordingBridge.intResult = 4

        val result = connection.sendControllerTouchEvent(1, 2, 5, 0.25f, 0.75f, 0.5f)

        assertThat(result).isEqualTo(4)
        assertSent("sendControllerTouchEvent", 1.toByte(), 2.toByte(), 5, 0.25f, 0.75f, 0.5f)
    }

    @Test
    fun `a controller motion sends its fields in order and returns the answer of the library`() {
        ShadowRecordingBridge.intResult = 5

        val result = connection.sendControllerMotionEvent(1, 2, 0.25f, 0.5f, 0.75f)

        assertThat(result).isEqualTo(5)
        assertSent("sendControllerMotionEvent", 1.toByte(), 2.toByte(), 0.25f, 0.5f, 0.75f)
    }

    @Test
    fun `a controller battery sends its fields in order`() {
        connection.sendControllerBatteryEvent(1, 3, 80)

        assertSent("sendControllerBatteryEvent", 1.toByte(), 3.toByte(), 80.toByte())
    }

    @Test
    fun `typed text is sent as standard UTF-8 bytes`() {
        val text = "a é 😀 \u0000 z"

        connection.sendUtf8Text(text)

        assertThat(sent().name).isEqualTo("sendUtf8Text")
        assertThat(sent().args.single() as ByteArray).isEqualTo(text.toByteArray(Charsets.UTF_8))
    }

    @Test
    fun `no text is a failure before anything is sent`() {
        assertThrows(RuntimeException::class.java) { connection.sendUtf8Text(null) }

        assertThat(ShadowRecordingBridge.calls).isEmpty()
    }

    @Test
    fun `the external address for mDNS is what the library finds for the STUN server`() {
        ShadowRecordingBridge.externalAddress = "203.0.113.9"

        val found = NvConnection.findExternalAddressForMdns("stun.example.org", 3478)

        assertThat(found).isEqualTo("203.0.113.9")
        assertSent("findExternalAddressIP4", "stun.example.org", 3478)
    }

    @Test
    fun `no external address is null`() {
        assertThat(NvConnection.findExternalAddressForMdns("stun.example.org", 3478)).isNull()
    }

    @Test
    fun `a user that is a monkey sends no input`() {
        val monkey = monkeyConnection()

        monkey.sendMouseMove(1, 2)
        monkey.sendMousePosition(1, 2, 3, 4)
        monkey.sendMouseMoveAsMousePosition(1, 2, 3, 4)
        monkey.sendMouseButtonDown(1)
        monkey.sendMouseButtonUp(1)
        monkey.sendControllerInput(1, 2, 3, 4, 5, 6, 7, 8, 9)
        monkey.sendKeyboardInput(1, 2, 3, 4)
        monkey.sendMouseScroll(1)
        monkey.sendMouseHScroll(1)
        monkey.sendMouseHighResScroll(1)
        monkey.sendMouseHighResHScroll(1)
        monkey.sendUtf8Text("text")

        assertThat(ShadowRecordingBridge.calls).isEmpty()
    }

    @Test
    fun `a monkey's touch, pen, controller touch and motion are unsupported`() {
        ShadowRecordingBridge.intResult = 0
        val monkey = monkeyConnection()

        val results =
            listOf(
                monkey.sendTouchEvent(1, 2, 3f, 4f, 5f, 6f, 7f, 8),
                monkey.sendPenEvent(1, 2, 3, 4f, 5f, 6f, 7f, 8f, 9, 10),
                monkey.sendControllerTouchEvent(1, 2, 3, 4f, 5f, 6f),
                monkey.sendControllerMotionEvent(1, 2, 3f, 4f, 5f),
            )

        assertThat(results).containsExactly(-5501, -5501, -5501, -5501)
        assertThat(MoonBridge.LI_ERR_UNSUPPORTED).isEqualTo(-5501)
        assertThat(ShadowRecordingBridge.calls).isEmpty()
    }

    @Test
    fun `a monkey still announces a controller and reports its battery`() {
        ShadowRecordingBridge.intResult = 6
        val monkey = monkeyConnection()

        val arrival = monkey.sendControllerArrivalEvent(1, 2, 3, 4, 5)
        monkey.sendControllerBatteryEvent(1, 2, 3)

        assertThat(arrival).isEqualTo(6)
        assertThat(ShadowRecordingBridge.names)
            .containsExactly("sendControllerArrivalEvent", "sendControllerBatteryEvent")
            .inOrder()
    }

    @Test
    fun `whether the user is a monkey is read when the connection is built`() {
        val before = rig.connection(config = null)
        ShadowMonkeyActivityManager.monkey = true
        try {
            before.sendMouseMove(1, 2)
        } finally {
            ShadowMonkeyActivityManager.monkey = false
        }

        assertThat(ShadowRecordingBridge.names).containsExactly("sendMouseMove")
    }

    private fun monkeyConnection(): NvConnection {
        ShadowMonkeyActivityManager.monkey = true
        try {
            assertThat(ActivityManager.isUserAMonkey()).isTrue()
            return rig.connection(config = null)
        } finally {
            ShadowMonkeyActivityManager.monkey = false
        }
    }
}
