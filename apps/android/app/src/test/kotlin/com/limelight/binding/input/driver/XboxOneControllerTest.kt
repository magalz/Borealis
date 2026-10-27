package com.limelight.binding.input.driver

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import io.github.magalz.borealis.testing.LimeLogCapture
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Characterizes the Xbox One driver through its public side: the packets it sends to the OUT
 * endpoint, and what reaches the listener from the reports a pad plays on the IN endpoint.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    shadows =
        [
            ShadowRecordingBridge::class,
            ShadowScriptedUsbConnection::class,
            ShadowRequestingUsbManager::class,
        ]
)
class XboxOneControllerTest {
    @get:Rule val scriptedUsb = ScriptedUsbReset()

    @get:Rule val noThreadDeaths = NoUncaughtThreadExceptions()

    @get:Rule val log = LimeLogCapture()

    private val pads = mutableListOf<Pad<XboxOneController>>()

    @After fun stopPads() = pads.forEach { it.stopIfRunning() }

    private fun pad(
        vendorId: Int = 0x045E,
        productId: Int = 0x028E,
        id: Int = 0,
    ): Pad<XboxOneController> =
        Pad(UsbPads.xboxOnePad(vendorId = vendorId, productId = productId)) {
                device,
                connection,
                listener ->
                XboxOneController(device, connection, id, listener)
            }
            .also(pads::add)

    // The packets the init sends, with the sequence number the driver puts in byte 2.
    private fun fw2015(seq: Int) = listOf(0x05, 0x20, seq, 0x01, 0x00)

    private fun oneS(seq: Int) = listOf(0x05, 0x20, seq, 0x0F, 0x06)

    private fun hori(seq: Int) =
        listOf(0x01, 0x20, seq, 0x09, 0x00, 0x04, 0x20, 0x3A, 0x00, 0x00, 0x00, 0x80, 0x00)

    private fun pdp1(seq: Int) = listOf(0x0A, 0x20, seq, 0x03, 0x00, 0x01, 0x14)

    private fun pdp2(seq: Int) = listOf(0x06, 0x20, seq, 0x02, 0x01, 0x00)

    private fun rumbleInit1(seq: Int) =
        listOf(0x09, 0x00, seq, 0x09, 0x00, 0x0F, 0x00, 0x00, 0x1D, 0x1D, 0xFF, 0x00, 0x00)

    private fun rumbleInit2(seq: Int) =
        listOf(0x09, 0x00, seq, 0x09, 0x00, 0x0F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)

    private fun initPackets(vendorId: Int, productId: Int): List<List<Int>> {
        val pad = pad(vendorId, productId)
        assertThat(pad.start()).isTrue()
        return pad.writtenBytes()
    }

    @Test
    fun `the controller describes itself to the host`() {
        val pad = pad(vendorId = 0x0F0D, productId = 0x0067, id = 2)

        assertThat(pad.controller.viaGetter("getControllerId")).isEqualTo(2)
        assertThat(pad.controller.viaGetter("getVendorId")).isEqualTo(0x0F0D)
        assertThat(pad.controller.viaGetter("getProductId")).isEqualTo(0x0067)
        assertThat(pad.controller.viaGetter("getType")).isEqualTo(1.toByte()) // LI_CTYPE_XBOX
        // LI_CCAP_ANALOG_TRIGGERS | LI_CCAP_RUMBLE | LI_CCAP_TRIGGER_RUMBLE
        assertThat(pad.controller.viaGetter("getCapabilities")).isEqualTo(7.toShort())
        assertThat(pad.controller.viaGetter("getSupportedButtonFlags")).isEqualTo(0)
    }

    @Test
    fun `an Xbox One S, X or Series pad gets the 2015 init and its own mode packet`() {
        for (productId in listOf(0x02EA, 0x02FE, 0x0B00, 0x0B05, 0x0B12, 0x0B13)) {
            assertThat(initPackets(0x045E, productId)).containsExactly(fw2015(0), oneS(1)).inOrder()
        }
    }

    @Test
    fun `a Microsoft pad that is none of those gets only the 2015 init`() {
        assertThat(initPackets(0x045E, 0x028E)).containsExactly(fw2015(0))
    }

    @Test
    fun `a Hori pad of the first product gets the Hori init before the 2015 one`() {
        assertThat(initPackets(0x0F0D, 0x0067)).containsExactly(hori(0), fw2015(1)).inOrder()
        assertThat(initPackets(0x0F0D, 0x0001)).containsExactly(fw2015(0))
    }

    @Test
    fun `a PDP pad gets both PDP inits, and the Hori product of that vendor gets Hori first`() {
        assertThat(initPackets(0x0E6F, 0x0165))
            .containsExactly(hori(0), fw2015(1), pdp1(2), pdp2(3))
            .inOrder()
        assertThat(initPackets(0x0E6F, 0x02A0))
            .containsExactly(fw2015(0), pdp1(1), pdp2(2))
            .inOrder()
    }

    @Test
    fun `a PowerA pad of the rumble products gets the 2015 init and both rumble inits`() {
        for (productId in listOf(0x541A, 0x542A, 0x543A)) {
            assertThat(initPackets(0x24C6, productId))
                .containsExactly(fw2015(0), rumbleInit1(1), rumbleInit2(2))
                .inOrder()
        }
        assertThat(initPackets(0x24C6, 0x0001)).containsExactly(fw2015(0))
    }

    @Test
    fun `the other vendors get the 2015 init`() {
        for (vendorId in listOf(0x0738, 0x1532, 0x20D6, 0x2E24, 0x2DC8)) {
            assertThat(initPackets(vendorId, 0x0001)).containsExactly(fw2015(0))
        }
    }

    @Test
    fun `an init packet goes to the OUT endpoint with a three second timeout`() {
        val pad = pad()

        pad.start()

        assertThat(pad.usb.writes.single().timeoutMs).isEqualTo(3000)
        assertThat(pad.usb.writes.single().endpoint.address).isEqualTo(0x01)
    }

    @Test
    fun `an init packet that does not go through fails the start and stops the init`() {
        val pad = pad(vendorId = 0x045E, productId = 0x02EA)
        // The 2015 packet is five bytes; the second one fails.
        pad.usb.writeResults.addAll(listOf(5, 4))

        assertThat(pad.start()).isFalse()

        assertThat(pad.writtenBytes()).containsExactly(fw2015(0), oneS(1)).inOrder()
        assertThat(log.warnings()).containsExactly("Initialization transfer failed: 4")
    }

    @Test
    fun `start fails when an interface cannot be claimed or an endpoint is missing`() {
        val claimFails = pad()
        claimFails.usb.claimResults.add(false)
        assertThat(claimFails.start()).isFalse()
        assertThat(claimFails.usb.claims.single().force).isTrue()
        assertThat(claimFails.usb.writes).isEmpty()
        assertThat(log.warnings()).containsExactly("Failed to claim interfaces")

        val noOut =
            Pad(
                UsbPads.device(
                    "/dev/noout",
                    0x045E,
                    0x02EA,
                    listOf(
                        UsbPads.usbInterface(
                            0,
                            subclass = UsbPads.XB1_SUBCLASS,
                            protocol = UsbPads.XB1_PROTOCOL,
                            endpoints = listOf(UsbPads.inEndpoint()),
                        )
                    ),
                )
            ) { device, connection, listener ->
                XboxOneController(device, connection, 0, listener)
            }
        assertThat(noOut.start()).isFalse()
        assertThat(noOut.usb.writes).isEmpty()
        assertThat(log.warnings())
            .containsExactly("Failed to claim interfaces", "Missing required endpoint")
            .inOrder()
    }

    // A 0x20 report: the type, three bytes the driver skips, the two button bytes, the two
    // triggers (16 bits each) and the four axes.
    private fun buttonsReport(
        first: Int = 0,
        second: Int = 0,
        leftTrigger: Int = 0,
        rightTrigger: Int = 0,
        sticks: RawSticks = RawSticks(),
    ): IntArray =
        intArrayOf(0x20, 0x00, 0x01, 0x0E) +
            intArrayOf(first, second) +
            listOf(
                    leftTrigger,
                    rightTrigger,
                    sticks.leftX,
                    sticks.leftY,
                    sticks.rightX,
                    sticks.rightY,
                )
                .flatMap { listOf(it and 0xFF, (it shr 8) and 0xFF) }

    private fun Pad<XboxOneController>.play(vararg reports: IntArray) = reports.forEach {
        usb.report(*it)
    }

    private fun axis(raw: Int) = raw / 32767.0f

    // The pad's Y axis points down: the driver reports the bitwise complement of the raw value.
    private fun invertedAxis(raw: Int) = raw.inv() / 32767.0f

    @Test
    fun `every button of the first button byte sets only its flag`() {
        val pad = pad()
        val bits =
            listOf(
                0x04 to ControllerPacket.PLAY_FLAG,
                0x08 to ControllerPacket.BACK_FLAG,
                0x10 to ControllerPacket.A_FLAG,
                0x20 to ControllerPacket.B_FLAG,
                0x40 to ControllerPacket.X_FLAG,
                0x80 to ControllerPacket.Y_FLAG,
                0x01 to 0,
                0x02 to 0,
            )
        pad.play(*bits.map { (bit, _) -> buttonsReport(first = bit) }.toTypedArray())

        pad.start()

        // The Guide flag is not in these reports: it stays as the controller started with it.
        assertThat(pad.listener.states(bits.size).map { it.buttonFlags and GUIDE.inv() })
            .containsExactlyElementsIn(bits.map { it.second })
            .inOrder()
    }

    @Test
    fun `every button of the second button byte sets only its flag`() {
        val pad = pad()
        val bits =
            listOf(
                0x01 to ControllerPacket.UP_FLAG,
                0x02 to ControllerPacket.DOWN_FLAG,
                0x04 to ControllerPacket.LEFT_FLAG,
                0x08 to ControllerPacket.RIGHT_FLAG,
                0x10 to ControllerPacket.LB_FLAG,
                0x20 to ControllerPacket.RB_FLAG,
                0x40 to ControllerPacket.LS_CLK_FLAG,
                0x80 to ControllerPacket.RS_CLK_FLAG,
            )
        pad.play(*bits.map { (bit, _) -> buttonsReport(second = bit) }.toTypedArray())

        pad.start()

        assertThat(pad.listener.states(bits.size).map { it.buttonFlags and GUIDE.inv() })
            .containsExactlyElementsIn(bits.map { it.second })
            .inOrder()
    }

    @Test
    fun `a report replaces the buttons of the buttons report, so a released button is cleared`() {
        val pad = pad()
        pad.play(
            buttonsReport(first = 0xFF, second = 0xFF),
            buttonsReport(),
            buttonsReport(first = 0x10),
        )

        pad.start()

        val flags = pad.listener.states(3).map { it.buttonFlags and GUIDE.inv() }
        assertThat(flags[0])
            .isEqualTo(
                ControllerPacket.A_FLAG or
                    ControllerPacket.B_FLAG or
                    ControllerPacket.X_FLAG or
                    ControllerPacket.Y_FLAG or
                    ControllerPacket.UP_FLAG or
                    ControllerPacket.DOWN_FLAG or
                    ControllerPacket.LEFT_FLAG or
                    ControllerPacket.RIGHT_FLAG or
                    ControllerPacket.LB_FLAG or
                    ControllerPacket.RB_FLAG or
                    ControllerPacket.PLAY_FLAG or
                    ControllerPacket.BACK_FLAG or
                    ControllerPacket.LS_CLK_FLAG or
                    ControllerPacket.RS_CLK_FLAG
            )
        assertThat(flags[1]).isEqualTo(0)
        assertThat(flags[2]).isEqualTo(ControllerPacket.A_FLAG)
    }

    @Test
    fun `the Guide button is down in the first report because the controller starts with it down`() {
        // The controller starts with every button flag set, and a buttons report does not touch
        // the Guide flag: only a mode report does. So the first reports of a pad say Guide is down.
        val pad = pad()
        pad.play(buttonsReport(), buttonsReport(first = 0x10))

        pad.start()

        val flags = pad.listener.states(2).map { it.buttonFlags }
        assertThat(flags[0]).isEqualTo(GUIDE)
        assertThat(flags[1]).isEqualTo(GUIDE or ControllerPacket.A_FLAG)
    }

    @Test
    fun `the triggers are signed shorts over 1023 and are not clamped`() {
        val pad = pad()
        val raws = listOf(0, 1023, 512, 2046, -1, 32767, -32768)
        pad.play(
            *raws
                .map { raw -> buttonsReport(leftTrigger = raw, rightTrigger = 1023 - raw) }
                .toTypedArray()
        )

        pad.start()

        val states = pad.listener.states(raws.size)
        assertThat(states.map { it.leftTrigger })
            .containsExactlyElementsIn(raws.map { it / 1023.0f })
            .inOrder()
        assertThat(states.map { it.rightTrigger })
            .containsExactlyElementsIn(raws.map { (1023 - it).toShort() / 1023.0f })
            .inOrder()
        assertThat(states[1].leftTrigger).isEqualTo(1.0f)
        assertThat(states[3].leftTrigger).isEqualTo(2.0f)
        assertThat(states[4].leftTrigger).isLessThan(0.0f)
    }

    @Test
    fun `the sticks are signed shorts over 32767, with the Y axes inverted`() {
        val pad = pad()
        val raws = listOf(-32768, 32767, 0, 1, -1)
        pad.play(
            *raws
                .map { raw ->
                    buttonsReport(sticks = RawSticks(raw, raw, raw, raw))
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
        assertThat(states[0].leftStickX).isEqualTo(-32768 / 32767.0f)
        assertThat(states[0].leftStickY).isEqualTo(1.0f)
        assertThat(states[1].leftStickX).isEqualTo(1.0f)
        assertThat(states[1].leftStickY).isEqualTo(-32768 / 32767.0f)
    }

    @Test
    fun `each axis and trigger is read from its own place in the report`() {
        val pad = pad(id = 4)
        pad.play(
            buttonsReport(
                leftTrigger = 100,
                rightTrigger = 200,
                sticks = RawSticks(1000, 2000, -3000, -4000),
            )
        )

        pad.start()

        val state = pad.listener.states(1).single()
        assertThat(state.controllerId).isEqualTo(4)
        assertThat(state.leftTrigger).isEqualTo(100 / 1023.0f)
        assertThat(state.rightTrigger).isEqualTo(200 / 1023.0f)
        assertThat(state.leftStickX).isEqualTo(axis(1000))
        assertThat(state.leftStickY).isEqualTo(invertedAxis(2000))
        assertThat(state.rightStickX).isEqualTo(axis(-3000))
        assertThat(state.rightStickY).isEqualTo(invertedAxis(-4000))
    }

    @Test
    fun `the three bytes after the type of a buttons report are not read`() {
        val pad = pad()
        // The three bytes after the type are the ones the driver skips.
        pad.play(buttonsReport(first = 0x10).apply { fill(0xFF, 1, 4) })

        pad.start()

        val state = pad.listener.states(1).single()
        assertThat(state.buttonFlags and GUIDE.inv()).isEqualTo(ControllerPacket.A_FLAG)
        assertThat(state.leftTrigger).isEqualTo(0.0f)
    }

    @Test
    fun `a buttons report needs eighteen bytes and one with fewer reports nothing`() {
        val pad = pad()
        val full = buttonsReport(first = 0x20)
        assertThat(full).hasLength(18)
        pad.play(full.copyOf(17), buttonsReport(first = 0x40), full.copyOf(1))
        pad.play(buttonsReport(first = 0x80))

        pad.start()

        // The 17-byte and the 1-byte reads report nothing; the 18-byte ones do.
        assertThat(pad.listener.states(2).map { it.buttonFlags and GUIDE.inv() })
            .containsExactly(ControllerPacket.X_FLAG, ControllerPacket.Y_FLAG)
            .inOrder()
        assertThat(log.severes())
            .containsExactly(
                "XBone button/axis read too small: 16",
                "XBone button/axis read too small: 0",
            )
            .inOrder()
    }

    // A 0x07 mode report: the type, a flags byte, a sequence number, a byte the driver skips and
    // the Guide byte.
    private fun modeReport(flags: Int = 0x20, seq: Int = 0x01, guide: Int = 0): IntArray =
        intArrayOf(0x07, flags, seq, 0x02, guide)

    @Test
    fun `a mode report sets and clears the Guide flag and nothing else`() {
        val pad = pad()
        pad.play(
            buttonsReport(first = 0x10),
            modeReport(guide = 0x00),
            modeReport(guide = 0x01),
            modeReport(guide = 0x02),
            modeReport(guide = 0xFF),
        )

        pad.start()

        val flags = pad.listener.states(5).map { it.buttonFlags }
        assertThat(flags[0]).isEqualTo(GUIDE or ControllerPacket.A_FLAG)
        assertThat(flags[1]).isEqualTo(ControllerPacket.A_FLAG)
        assertThat(flags[2]).isEqualTo(GUIDE or ControllerPacket.A_FLAG)
        // Only bit 0 of the Guide byte counts.
        assertThat(flags[3]).isEqualTo(ControllerPacket.A_FLAG)
        assertThat(flags[4]).isEqualTo(GUIDE or ControllerPacket.A_FLAG)
    }

    @Test
    fun `a mode report that wants no acknowledgement is not answered`() {
        val pad = pad()
        pad.play(modeReport(flags = 0x20, guide = 0x01))

        pad.start()
        pad.listener.states(1)

        assertThat(pad.writtenBytes()).containsExactly(fw2015(0))
    }

    @Test
    fun `a mode report of type 0x30 is acknowledged with its own sequence number`() {
        val pad = pad()
        pad.play(modeReport(flags = 0x30, seq = 0xA0, guide = 0x01))

        pad.start()
        val state = pad.listener.states(1).single()

        assertThat(state.buttonFlags and GUIDE).isEqualTo(GUIDE)
        // After the 2015 init, the ack: the mode packet header, the report's sequence number.
        assertThat(pad.writtenBytes())
            .containsExactly(
                fw2015(0),
                listOf(0x01, 0x20, 0xA0, 0x09, 0x00, 0x07, 0x20, 0x02, 0, 0, 0, 0, 0),
            )
            .inOrder()
        assertThat(pad.usb.writes.last().timeoutMs).isEqualTo(3000)
    }

    @Test
    fun `an acknowledged mode report still reads the Guide byte after the skipped one`() {
        val pad = pad()
        pad.play(
            // flags 0x30, seq, skipped, guide down
            intArrayOf(0x07, 0x30, 0x05, 0xEE, 0x01),
            // flags 0x30, seq, skipped, guide up
            intArrayOf(0x07, 0x30, 0x06, 0xEE, 0x00),
        )

        pad.start()

        assertThat(pad.listener.states(2).map { it.buttonFlags and GUIDE })
            .containsExactly(GUIDE, 0)
            .inOrder()
    }

    @Test
    fun `a mode report needs five bytes and one with fewer reports nothing`() {
        val pad = pad()
        pad.play(
            modeReport().copyOf(4),
            modeReport(flags = 0x30).copyOf(4),
            buttonsReport(first = 0x10),
        )

        pad.start()

        val state = pad.listener.states(1).single()
        // Nothing of the short ones reached the listener, and the short 0x30 one was not answered.
        assertThat(state.buttonFlags).isEqualTo(GUIDE or ControllerPacket.A_FLAG)
        assertThat(pad.writtenBytes()).containsExactly(fw2015(0))
        assertThat(log.severes())
            .containsExactly("XBone mode read too small: 3", "XBone mode read too small: 3")
    }

    @Test
    fun `a report of any other type reports nothing`() {
        val pad = pad()
        pad.play(
            intArrayOf(
                0x03,
                0x20,
                0x01,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
            ),
            intArrayOf(0x00, 0x20, 0x10),
            intArrayOf(0x0A, 0x20, 0x10),
            buttonsReport(first = 0x10),
        )

        pad.start()

        assertThat(pad.listener.states(1).single().buttonFlags)
            .isEqualTo(GUIDE or ControllerPacket.A_FLAG)
    }

    @Test
    fun `rumble sends the main motors and keeps the trigger motors`() {
        val pad = pad()
        pad.start()

        pad.controller.rumble(0x7FFF, 0x4000)
        pad.controller.rumbleTriggers(0x7FFF, 0x0200)
        pad.controller.rumble(0x8000.toShort(), 0xFFFF.toShort())
        pad.controller.rumble(0x01FF, 0x0200)

        // Byte 2 is the sequence number, 6 and 7 the trigger motors, 8 and 9 the main motors.
        fun packet(seq: Int, left: Int, right: Int, low: Int, high: Int) =
            listOf(0x09, 0x00, seq, 0x09, 0x00, 0x0F, left, right, low, high, 0xFF, 0x00, 0xFF)
        assertThat(pad.writtenBytes().drop(1))
            .containsExactly(
                packet(1, 0x00, 0x00, 0x3F, 0x20),
                packet(2, 0x3F, 0x01, 0x3F, 0x20),
                packet(3, 0x3F, 0x01, 0xC0, 0xFF),
                packet(4, 0x3F, 0x01, 0x00, 0x01),
            )
            .inOrder()
        assertThat(pad.usb.writes.drop(1).map { it.timeoutMs }.toSet()).containsExactly(100)
    }

    @Test
    fun `the sequence number of the rumble packets continues the init and wraps as a byte`() {
        // A PDP pad: its init uses sequence numbers 0 to 3.
        val pad = pad(vendorId = 0x0E6F, productId = 0x0165)
        pad.start()

        repeat(258) { pad.controller.rumble(0, 0) }

        val sequence = pad.writtenBytes().drop(4).map { it[2] }
        assertThat(sequence).isEqualTo(List(258) { (4 + it) and 0xFF })
        // The signed-byte limit and the unsigned one, spelled out.
        assertThat(sequence[127 - 4]).isEqualTo(0x7F)
        assertThat(sequence[128 - 4]).isEqualTo(0x80)
        assertThat(sequence[255 - 4]).isEqualTo(0xFF)
        assertThat(sequence[256 - 4]).isEqualTo(0x00)
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
    fun `stopping the pad cuts the main motors, leaves the trigger motors and closes it`() {
        val pad = pad()
        pad.start()
        pad.controller.rumbleTriggers(0x7FFF, 0x7FFF)
        pad.controller.rumble(0x7FFF, 0x7FFF)

        pad.controller.stop()
        pad.controller.stop()

        // The 2015 init, two rumbles, then the stop's rumble with the main motors at zero: the
        // trigger motors keep the value they had.
        assertThat(pad.writtenBytes().last())
            .isEqualTo(
                listOf(0x09, 0x00, 3, 0x09, 0x00, 0x0F, 0x3F, 0x3F, 0x00, 0x00, 0xFF, 0x00, 0xFF)
            )
        assertThat(pad.usb.writes).hasSize(4)
        assertThat(pad.usb.closes).isEqualTo(1)
        val removed = pad.listener.next()
        assertThat((removed as PadEvent.Removed).controller).isSameInstanceAs(pad.controller)
    }

    private companion object {
        const val GUIDE = ControllerPacket.SPECIAL_BUTTON_FLAG
    }
}
