package io.github.magalz.borealis.input.devices

import android.view.KeyEvent.KEYCODE_BUTTON_A
import android.view.MotionEvent
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.TimeUnit
import org.junit.Test

/**
 * A pad that the host painted keeps a session with the device's lights open. The handler closes it
 * when the context goes (the device is removed, or the handler stops) and hands it to the new
 * context when the device only changed, so that the color stays and the host sees no gap. The
 * session is the framework's `LightsManager.LightsSession`; the tests give the context one of their
 * own ([recordingLightsSession]), which records what the handler asks of it.
 */
class LightsSessionLifecycleTest : DevicesTestBase() {
    private val hat = listOf(MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y).map { Axis(it) }

    private fun paintedPad(id: Int = 1): Pad {
        DeviceHardware.set(id, Hardware(rgbLights = 1))
        return pad(id)
    }

    @Test
    fun `removing a device closes its lights session`() {
        val device = paintedPad()
        val rig = rig(devices = listOf(device))
        val session = recordingLightsSession()
        setField(rig.touch(device).context, "lightsSession", session)

        rig.disconnect(device)

        assertThat(session.recorded.closes).hasSize(1)
        assertThat(rig.contextOf(device)).isNull()
    }

    @Test
    fun `a session that fails to close does not stop the removal`() {
        val device = paintedPad()
        val rig = rig(multiController = true, devices = listOf(device))
        rig.press(device, KEYCODE_BUTTON_A)
        val session = recordingLightsSession()
        session.recorded.closeFailure = IllegalStateException("closed already")
        setField(rig.context(device).context, "lightsSession", session)

        rig.disconnect(device)

        assertThat(session.recorded.closes).hasSize(1)
        assertThat(rig.contextOf(device)).isNull()
        assertThat(rig.connection.packets.last())
            .isEqualTo(Sent(controller = 0, activeMask = 0, buttons = 0))
    }

    @Test
    fun `stopping closes the lights session of every device`() {
        val first = paintedPad(1)
        val second = paintedPad(2)
        val rig = rig(devices = listOf(first, second))
        val sessions = listOf(recordingLightsSession(), recordingLightsSession())
        setField(rig.touch(first).context, "lightsSession", sessions[0])
        setField(rig.touch(second).context, "lightsSession", sessions[1])

        rig.handler.stop()

        assertThat(sessions.map { it.recorded.closes.size }).containsExactly(1, 1)
    }

    @Test
    fun `a change hands the lights session to the new context without closing it`() {
        val before = paintedPad()
        val rig = rig(devices = listOf(before))
        val old = rig.touch(before)
        val session = recordingLightsSession()
        setField(old.context, "lightsSession", session)

        val after = pad(1) { axes = STICKS_AND_Z + hat }
        disconnectPad(before)
        connectPad(after)
        rig.change(after)

        assertThat(rig.context(after)["lightsSession"]).isSameInstanceAs(session)
        assertThat(old["lightsSession"]).isNull()
        assertThat(session.recorded.closes).isEmpty()
    }

    @Test
    fun `a change sends the color of the pad to its lights again`() {
        val before = paintedPad()
        val rig = rig(devices = listOf(before))
        val old = rig.touch(before)
        val session = recordingLightsSession()
        setField(old.context, "lightsSession", session)
        setField(old.context, "ledArgbValue", 0xFF112233.toInt())

        val after = pad(1) { axes = STICKS_AND_Z + hat }
        disconnectPad(before)
        connectPad(after)
        rig.change(after)

        // The handler's background thread paints the new context's lights
        assertThat(session.recorded.firstRequest.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue()
        val request = session.recorded.requests.single()
        assertThat(request.lightStates.map { it.color }).containsExactly(0xFF112233.toInt())
    }

    private companion object {
        const val AWAIT_SECONDS = 10L
    }
}
