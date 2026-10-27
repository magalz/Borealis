package com.limelight.binding.input.driver

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import com.google.common.truth.Truth.assertThat
import com.limelight.binding.input.driver.ShadowScriptedUsbConnection.Read
import com.limelight.nvstream.input.ControllerPacket
import io.github.magalz.borealis.settings.ShadowMoonBridge
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Characterizes the Xbox 360 wired driver through its public side: a pad that plays reports on its
 * IN endpoint, and what reaches the listener and the OUT endpoint. The input thread waits a second
 * before it reports the pad, so every test that reads reports takes about that long.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    shadows =
        [
            ShadowMoonBridge::class,
            ShadowScriptedUsbConnection::class,
            ShadowRequestingUsbManager::class,
        ]
)
class Xbox360ControllerTest {
    @get:Rule val scriptedUsb = ScriptedUsbReset()

    @get:Rule val noThreadDeaths = NoUncaughtThreadExceptions()

    @get:Rule val log = LimeLogCapture()

    private val pads = mutableListOf<Pad<Xbox360Controller>>()

    @After fun stopPads() = pads.forEach { it.stopIfRunning() }

    private fun pad(
        id: Int = 0,
        device: UsbDevice = UsbPads.xbox360Pad(interfaceCount = 3),
    ): Pad<Xbox360Controller> =
        Pad(device) { usbDevice, connection, listener ->
                Xbox360Controller(usbDevice, connection, id, listener)
            }
            .also(pads::add)

    /** A report of the pad: the two header bytes, two button bytes, two triggers, four axes. */
    private fun report(
        dpadByte: Int = 0,
        faceByte: Int = 0,
        leftTrigger: Int = 0,
        rightTrigger: Int = 0,
        sticks: RawSticks = RawSticks(),
    ): IntArray =
        intArrayOf(0x00, 0x14, dpadByte, faceByte, leftTrigger, rightTrigger) +
            listOf(sticks.leftX, sticks.leftY, sticks.rightX, sticks.rightY).flatMap {
                listOf(it and 0xFF, (it shr 8) and 0xFF)
            }

    private fun Pad<Xbox360Controller>.play(vararg reports: IntArray) = reports.forEach {
        usb.report(*it)
    }

    private fun axis(raw: Int) = raw / 32767.0f

    // The pad's Y axis points down: the driver reports the bitwise complement of the raw value.
    private fun invertedAxis(raw: Int) = raw.inv() / 32767.0f

    @Test
    fun `the controller describes itself to the host`() {
        val device = UsbPads.xbox360Pad(vendorId = 0x046D, productId = 0xC21D)
        val pad = pad(id = 6, device = device)

        assertThat(pad.controller.viaGetter("getControllerId")).isEqualTo(6)
        assertThat(pad.controller.viaGetter("getVendorId")).isEqualTo(0x046D)
        assertThat(pad.controller.viaGetter("getProductId")).isEqualTo(0xC21D)
        assertThat(pad.controller.viaGetter("getType")).isEqualTo(1.toByte()) // LI_CTYPE_XBOX
        // LI_CCAP_ANALOG_TRIGGERS | LI_CCAP_RUMBLE
        assertThat(pad.controller.viaGetter("getCapabilities")).isEqualTo(3.toShort())
        assertThat(pad.controller.viaGetter("getSupportedButtonFlags")).isEqualTo(0)
    }

    @Test
    fun `start claims every interface by force and lights the LED`() {
        val pad = pad()

        assertThat(pad.start()).isTrue()

        assertThat(pad.usb.claims.map { it.usbInterface.id }).containsExactly(0, 1, 2).inOrder()
        assertThat(pad.usb.claims.map { it.force }).containsExactly(true, true, true)
        assertThat(pad.writtenBytes()).containsExactly(listOf(0x01, 0x03, 0x02))
        assertThat(pad.usb.writes.single().timeoutMs).isEqualTo(3000)
        assertThat(pad.usb.writes.single().endpoint.direction).isEqualTo(UsbConstants.USB_DIR_OUT)
        // The endpoints are those of the first interface: the other interfaces have numbers of
        // their own.
        assertThat(pad.usb.writes.single().endpoint.address).isEqualTo(0x01)
    }

    @Test
    fun `the LED is the one of the pad's slot, wrapping after four`() {
        val ledOfSlot =
            listOf(0, 1, 2, 3, 4, 5, 7).associateWith { id ->
                val pad = pad(id = id)
                pad.start()
                pad.writtenBytes().first()[2]
            }

        assertThat(ledOfSlot).containsExactly(0, 2, 1, 3, 2, 4, 3, 5, 4, 2, 5, 3, 7, 5).inOrder()
    }

    @Test
    fun `a failed LED command does not fail the start`() {
        val pad = pad()
        pad.usb.writeResults.add(-1)

        assertThat(pad.start()).isTrue()
        assertThat(pad.usb.writes).hasSize(1)
        assertThat(log.warnings()).containsExactly("LED set transfer failed: -1")
    }

    @Test
    fun `start fails when an interface cannot be claimed, and goes no further`() {
        val pad = pad()
        pad.usb.claimResults.addAll(listOf(true, false))

        assertThat(pad.start()).isFalse()

        assertThat(pad.usb.claims).hasSize(2)
        assertThat(pad.usb.writes).isEmpty()
        assertThat(log.warnings()).containsExactly("Failed to claim interfaces")
    }

    private fun startFailsWith(
        warning: String,
        vararg endpoints: android.hardware.usb.UsbEndpoint,
    ) {
        val device =
            UsbPads.device(
                "/dev/odd",
                0x045E,
                0x028E,
                listOf(
                    UsbPads.usbInterface(
                        0,
                        subclass = UsbPads.XB360_SUBCLASS,
                        protocol = UsbPads.XB360_PROTOCOL,
                        endpoints = endpoints.toList(),
                    )
                ),
            )
        val pad = pad(device = device)

        assertThat(pad.start()).isFalse()
        assertThat(pad.usb.writes).isEmpty()
        assertThat(log.warnings()).containsExactly(warning)
    }

    @Test
    fun `start fails on a second IN endpoint`() =
        startFailsWith(
            "Found duplicate IN endpoint",
            UsbPads.inEndpoint(1),
            UsbPads.outEndpoint(1),
            UsbPads.inEndpoint(2),
        )

    @Test
    fun `start fails on a second OUT endpoint`() =
        startFailsWith(
            "Found duplicate OUT endpoint",
            UsbPads.inEndpoint(1),
            UsbPads.outEndpoint(1),
            UsbPads.outEndpoint(2),
        )

    @Test
    fun `start fails without an IN endpoint`() =
        startFailsWith("Missing required endpoint", UsbPads.outEndpoint(1))

    @Test
    fun `start fails without an OUT endpoint`() =
        startFailsWith("Missing required endpoint", UsbPads.inEndpoint(1))

    @Test
    fun `the pad is announced after the first second and before any report`() {
        val pad = pad(id = 3)
        pad.play(report(faceByte = 0x10))

        val before = realMillis()
        pad.start()
        val first = pad.listener.next()
        val waited = realMillis() - before

        // The input thread lets the old Android input device go away before it announces the pad.
        // There is no upper bound: only the second is promised, not how much later.
        assertThat(waited).isAtLeast(950L)
        assertThat(first).isInstanceOf(PadEvent.Added::class.java)
        assertThat((first as PadEvent.Added).controller).isSameInstanceAs(pad.controller)
        val second = pad.listener.next()
        assertThat(second).isInstanceOf(PadEvent.State::class.java)
        assertThat((second as PadEvent.State).controllerId).isEqualTo(3)
    }

    @Test
    fun `every button of the first button byte sets only its flag`() {
        val pad = pad()
        val bits =
            listOf(
                0x01 to ControllerPacket.UP_FLAG,
                0x02 to ControllerPacket.DOWN_FLAG,
                0x04 to ControllerPacket.LEFT_FLAG,
                0x08 to ControllerPacket.RIGHT_FLAG,
                0x10 to ControllerPacket.PLAY_FLAG,
                0x20 to ControllerPacket.BACK_FLAG,
                0x40 to ControllerPacket.LS_CLK_FLAG,
                0x80 to ControllerPacket.RS_CLK_FLAG,
            )
        pad.play(*bits.map { (bit, _) -> report(dpadByte = bit) }.toTypedArray())

        pad.start()

        assertThat(pad.listener.states(bits.size).map { it.buttonFlags })
            .containsExactlyElementsIn(bits.map { it.second })
            .inOrder()
    }

    @Test
    fun `every button of the second button byte sets only its flag, and bit 3 is ignored`() {
        val pad = pad()
        val bits =
            listOf(
                0x01 to ControllerPacket.LB_FLAG,
                0x02 to ControllerPacket.RB_FLAG,
                0x04 to ControllerPacket.SPECIAL_BUTTON_FLAG,
                0x08 to 0,
                0x10 to ControllerPacket.A_FLAG,
                0x20 to ControllerPacket.B_FLAG,
                0x40 to ControllerPacket.X_FLAG,
                0x80 to ControllerPacket.Y_FLAG,
            )
        pad.play(*bits.map { (bit, _) -> report(faceByte = bit) }.toTypedArray())

        pad.start()

        assertThat(pad.listener.states(bits.size).map { it.buttonFlags })
            .containsExactlyElementsIn(bits.map { it.second })
            .inOrder()
    }

    @Test
    fun `a report replaces the whole button state, so a released button is cleared`() {
        val pad = pad()
        val all = 0xFF
        pad.play(report(dpadByte = all, faceByte = all), report(), report(faceByte = 0x10))

        pad.start()

        val flags = pad.listener.states(3).map { it.buttonFlags }
        // Fifteen buttons are down in the first report; the bit that has no button changes nothing.
        assertThat(flags[0])
            .isEqualTo(
                0x1000 or
                    0x2000 or
                    0x4000 or
                    0x8000 or
                    0x0001 or
                    0x0002 or
                    0x0004 or
                    0x0008 or
                    0x0100 or
                    0x0200 or
                    0x0010 or
                    0x0020 or
                    0x0040 or
                    0x0080 or
                    0x0400
            )
        assertThat(flags[1]).isEqualTo(0)
        assertThat(flags[2]).isEqualTo(ControllerPacket.A_FLAG)
    }

    @Test
    fun `the first report clears the buttons the controller starts with`() {
        // The controller starts with every button flag set. The first report overwrites all
        // fifteen.
        val pad = pad()
        pad.play(report())

        pad.start()

        assertThat(pad.listener.states(1).single().buttonFlags).isEqualTo(0)
    }

    @Test
    fun `the triggers are unsigned bytes over 255`() {
        val pad = pad()
        pad.play(
            report(leftTrigger = 0, rightTrigger = 0xFF),
            report(leftTrigger = 0x80, rightTrigger = 0x7F),
            report(leftTrigger = 0xFF, rightTrigger = 0x01),
        )

        pad.start()

        val states = pad.listener.states(3)
        assertThat(states.map { it.leftTrigger })
            .containsExactly(0.0f, 128 / 255.0f, 1.0f)
            .inOrder()
        assertThat(states.map { it.rightTrigger })
            .containsExactly(1.0f, 127 / 255.0f, 1 / 255.0f)
            .inOrder()
    }

    @Test
    fun `the sticks are signed shorts over 32767, with the Y axes inverted`() {
        val pad = pad()
        // The extremes, zero and the smallest steps around it, as the raw 16-bit values.
        val raws = listOf(-32768, 32767, 0, 1, -1)
        pad.play(
            *raws
                .map { raw ->
                    report(sticks = RawSticks(raw, raw, raw, raw))
                }
                .toTypedArray()
        )

        pad.start()

        val states = pad.listener.states(raws.size)
        assertThat(states.map { it.leftStickX })
            .containsExactlyElementsIn(raws.map(::axis))
            .inOrder()
        assertThat(states.map { it.rightStickX })
            .containsExactlyElementsIn(raws.map(::axis))
            .inOrder()
        assertThat(states.map { it.leftStickY })
            .containsExactlyElementsIn(raws.map(::invertedAxis))
            .inOrder()
        assertThat(states.map { it.rightStickY })
            .containsExactlyElementsIn(raws.map(::invertedAxis))
            .inOrder()
        // The two ends, spelled out: full left is a little past -1, full down is +1 when inverted.
        assertThat(states[0].leftStickX).isEqualTo(-32768 / 32767.0f)
        assertThat(states[0].leftStickY).isEqualTo(1.0f)
        assertThat(states[1].leftStickX).isEqualTo(1.0f)
        assertThat(states[1].leftStickY).isEqualTo(-32768 / 32767.0f)
    }

    @Test
    fun `each axis and trigger is read from its own place in the report`() {
        val pad = pad()
        pad.play(
            report(
                leftTrigger = 10,
                rightTrigger = 20,
                sticks = RawSticks(1000, 2000, -3000, -4000),
            )
        )

        pad.start()

        val state = pad.listener.states(1).single()
        assertThat(state.leftTrigger).isEqualTo(10 / 255.0f)
        assertThat(state.rightTrigger).isEqualTo(20 / 255.0f)
        assertThat(state.leftStickX).isEqualTo(axis(1000))
        assertThat(state.leftStickY).isEqualTo(invertedAxis(2000))
        assertThat(state.rightStickX).isEqualTo(axis(-3000))
        assertThat(state.rightStickY).isEqualTo(invertedAxis(-4000))
    }

    @Test
    fun `the first two bytes of a report are not read`() {
        val pad = pad()
        pad.usb.report(0xFF, 0xFF, 0x01, 0x10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)

        pad.start()

        val state = pad.listener.states(1).single()
        assertThat(state.buttonFlags).isEqualTo(ControllerPacket.UP_FLAG or ControllerPacket.A_FLAG)
    }

    @Test
    fun `a report shorter than fourteen bytes reports nothing and a later one still does`() {
        val pad = pad()
        val tooShort = report(faceByte = 0x10).copyOf(13)
        pad.play(tooShort, report(faceByte = 0x20))

        pad.start()

        val states = pad.listener.states(1)
        assertThat(states.single().buttonFlags).isEqualTo(ControllerPacket.B_FLAG)
        assertThat(log.severes()).containsExactly("Read too small: 13")
    }

    @Test
    fun `a report longer than fourteen bytes is read from its start`() {
        val pad = pad()
        pad.play(report(faceByte = 0x40) + intArrayOf(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF))

        pad.start()

        val state = pad.listener.states(1).single()
        assertThat(state.buttonFlags).isEqualTo(ControllerPacket.X_FLAG)
        assertThat(state.leftStickX).isEqualTo(0.0f)
    }

    @Test
    fun `the pad is read by one ordinary unnamed thread`() {
        val pad = pad()
        pad.play(report())

        pad.start()
        pad.listener.states(1)

        val thread = pad.usb.awaitReader()
        assertThat(thread.name).startsWith("Thread-")
        assertThat(thread.isDaemon).isFalse()
        assertThat(thread.priority).isEqualTo(Thread.NORM_PRIORITY)
        assertThat(pad.usb.readTimeouts.toSet()).containsExactly(3000)
        assertThat(thread).isNotSameInstanceAs(Thread.currentThread())
    }

    @Test
    fun `an interrupt of the reading thread ends it and does not stop the pad`() {
        val pad = pad()
        pad.start()
        assertThat(pad.listener.next()).isInstanceOf(PadEvent.Added::class.java)

        // Only the pad's own stop interrupts the thread, so a plain interrupt is a thread that is
        // told to end: the read that it waits on ends at its timeout and the thread leaves with no
        // more.
        val reader = pad.usb.awaitReader()
        reader.interrupt()
        reader.join(5_000)

        assertThat(reader.isAlive).isFalse()
        assertThat(pad.listener.quietFor(200)).isTrue()
        assertThat(pad.usb.closes).isEqualTo(0)
    }

    @Test
    fun `a transfer that fails at once stops the pad`() {
        val pad = pad()
        pad.usb.reads.add(Read.Failure(elapsedMs = 0))

        pad.start()

        assertThat(pad.listener.next()).isInstanceOf(PadEvent.Added::class.java)
        val removed = pad.listener.next()
        assertThat(removed).isInstanceOf(PadEvent.Removed::class.java)
        assertThat((removed as PadEvent.Removed).controller).isSameInstanceAs(pad.controller)
        assertThat(pad.usb.closes).isEqualTo(1)
        // The LED command, then the rumble that stops the motors.
        assertThat(pad.writtenBytes().last()).isEqualTo(listOf(0x00, 0x08, 0x00, 0, 0, 0, 0, 0))
        assertThat(log.warnings()).containsExactly("Detected device I/O error")
    }

    @Test
    fun `a transfer that returns no bytes counts as a failure`() {
        val pad = pad()
        pad.usb.reads.add(Read.Empty)

        pad.start()

        assertThat(pad.listener.next()).isInstanceOf(PadEvent.Added::class.java)
        assertThat(pad.listener.next()).isInstanceOf(PadEvent.Removed::class.java)
        assertThat(pad.usb.closes).isEqualTo(1)
        assertThat(log.warnings()).containsExactly("Detected device I/O error")
    }

    @Test
    fun `a failure that took 999 ms is a lost device`() {
        val pad = pad()
        pad.usb.reads.add(Read.Failure(elapsedMs = 999))

        pad.start()

        assertThat(pad.listener.next()).isInstanceOf(PadEvent.Added::class.java)
        assertThat(pad.listener.next()).isInstanceOf(PadEvent.Removed::class.java)
    }

    @Test
    fun `a failure that took a second or more is a timeout and the pad reads again`() {
        val pad = pad()
        pad.usb.reads.add(Read.Failure(elapsedMs = 1000))
        pad.usb.reads.add(Read.Failure(elapsedMs = 3000))
        pad.play(report(faceByte = 0x80))

        pad.start()

        val state = pad.listener.states(1).single()
        assertThat(state.buttonFlags).isEqualTo(ControllerPacket.Y_FLAG)
        assertThat(pad.usb.closes).isEqualTo(0)
        assertThat(log.warnings()).isEmpty()
    }

    @Test
    fun `stopping the pad turns the motors off, closes it and reports the removal once`() {
        val pad = pad()
        pad.start()

        pad.controller.stop()
        pad.controller.stop()

        assertThat(pad.writtenBytes())
            .containsExactly(listOf(0x01, 0x03, 0x02), listOf(0x00, 0x08, 0x00, 0, 0, 0, 0, 0))
            .inOrder()
        assertThat(pad.usb.writes.last().timeoutMs).isEqualTo(100)
        assertThat(pad.usb.closes).isEqualTo(1)
        val removed = pad.listener.next()
        assertThat((removed as PadEvent.Removed).controller).isSameInstanceAs(pad.controller)
        // The input thread, stopped in its first second, never announces the pad.
        assertThat(pad.listener.quietFor(300)).isTrue()
    }

    @Test
    fun `stopping a pad that waits for a report ends the reading`() {
        val pad = pad()
        pad.start()
        assertThat(pad.listener.next()).isInstanceOf(PadEvent.Added::class.java)
        // The thread makes its first read, and so waits for a report, a little after the arrival.
        val reader = pad.usb.awaitReader()

        pad.controller.stop()

        assertThat(pad.listener.next()).isInstanceOf(PadEvent.Removed::class.java)
        assertThat(pad.usb.closes).isEqualTo(1)
        assertThat(pad.listener.quietFor(200)).isTrue()
        // The stop interrupts the thread that waits for a report, and the thread ends.
        reader.join(5_000)
        assertThat(reader.isAlive).isFalse()
    }

    @Test
    fun `rumble sends the high bytes of both motors`() {
        val pad = pad()
        pad.start()
        val motors =
            listOf(
                0x7FFF.toShort() to 0x4000.toShort(),
                0x8000.toShort() to 0xFFFF.toShort(),
                0x00FF.toShort() to 0x0100.toShort(),
                0x1234.toShort() to 0xABCD.toShort(),
            )

        motors.forEach { (low, high) -> pad.controller.rumble(low, high) }

        // The first write is the LED command.
        assertThat(pad.writtenBytes().drop(1))
            .containsExactly(
                listOf(0x00, 0x08, 0x00, 0x7F, 0x40, 0x00, 0x00, 0x00),
                listOf(0x00, 0x08, 0x00, 0x80, 0xFF, 0x00, 0x00, 0x00),
                listOf(0x00, 0x08, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00),
                listOf(0x00, 0x08, 0x00, 0x12, 0xAB, 0x00, 0x00, 0x00),
            )
            .inOrder()
        assertThat(pad.usb.writes.drop(1).map { it.timeoutMs }.toSet()).containsExactly(100)
    }

    @Test
    fun `a rumble that does not go through is not an error`() {
        val pad = pad()
        pad.start()
        pad.usb.writeResults.add(-1)

        pad.controller.rumble(1, 2)

        assertThat(pad.usb.writes).hasSize(2)
        assertThat(log.warnings()).containsExactly("Rumble transfer failed: -1")
    }

    @Test
    fun `the Xbox 360 pad has no trigger motors`() {
        val pad = pad()
        pad.start()

        pad.controller.rumbleTriggers(0x7FFF, 0x7FFF)

        // Only the LED command was written.
        assertThat(pad.usb.writes).hasSize(1)
    }
}
