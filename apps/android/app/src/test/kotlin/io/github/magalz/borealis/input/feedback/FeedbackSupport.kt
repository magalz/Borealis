package io.github.magalz.borealis.input.feedback

import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.os.CombinedVibration
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import com.limelight.binding.input.driver.AbstractController
import com.limelight.binding.input.driver.UsbDriverListener
import com.limelight.preferences.PreferenceConfiguration
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After

/** The base of the feedback tests: makes the rigs and stops each one's thread after the test. */
open class FeedbackTestBase {
    private val rigs = mutableListOf<FeedbackRig>()

    @After
    fun closeRigs() {
        rigs.forEach { it.close() }
    }

    protected fun rig(
        phoneVibrator: Vibrator = Hardware.vibrator(),
        phoneVibratorManager: Any? = null,
        phoneSensors: SensorManager =
            Hardware.sensorManager(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE),
        configure: PreferenceConfiguration.() -> Unit = {},
    ): FeedbackRig =
        FeedbackRig(phoneVibrator, phoneVibratorManager, phoneSensors, configure).also {
            rigs += it
        }
}

/**
 * What a USB-driver pad receives: the motor values exactly as the handler passed them, as the
 * signed shorts they are. It reports its input the way the driver service does.
 */
class RecordingUsbController(id: Int, listener: UsbDriverListener) :
    AbstractController(id, listener, 0x045e, 0x028e) {
    /** The `(low, high)` pairs of the rumble calls. */
    val rumbles = CopyOnWriteArrayList<Pair<Int, Int>>()

    /** The `(left, right)` pairs of the trigger rumble calls. */
    val triggerRumbles = CopyOnWriteArrayList<Pair<Int, Int>>()

    override fun start() = true

    override fun stop() {
        // Nothing is held open.
    }

    override fun rumble(lowFreqMotor: Short, highFreqMotor: Short) {
        rumbles += lowFreqMotor.toInt() to highFreqMotor.toInt()
    }

    override fun rumbleTriggers(leftTrigger: Short, rightTrigger: Short) {
        triggerRumbles += leftTrigger.toInt() to rightTrigger.toInt()
    }

    /** Reports a state with [buttons] held, which gives the pad its controller number. */
    fun report(buttons: Int) {
        buttonFlags = buttons
        reportInput()
    }
}

/** The effects and attributes the handler hands a single vibrator, on every Android version. */
object Effects {
    /** The one-shot effect of the handler's rumble: 60 seconds at [amplitude]. */
    fun oneShot(amplitude: Int): VibrationEffect = VibrationEffect.createOneShot(60_000, amplitude)

    /**
     * The waveform of a vibrator without amplitude control: off, [on] ms on, [off] ms off,
     * repeating.
     */
    fun waveform(on: Long, off: Long): VibrationEffect =
        VibrationEffect.createWaveform(longArrayOf(0, on, off), 0)

    /** The attributes of the handler's vibration before Android 13. */
    fun gameAudio(): AudioAttributes =
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).build()
}

/**
 * The vibration objects the handler is expected to hand a vibrator manager. Android 12 and later.
 */
object VibrationExpectations {
    private fun oneShot(amplitude: Int) = Effects.oneShot(amplitude)

    /** A parallel vibration of one-shot effects: the vibrator id and its amplitude. */
    fun combo(vararg amplitudes: Pair<Int, Int>): CombinedVibration {
        val parallel = CombinedVibration.startParallel()
        for ((id, amplitude) in amplitudes) {
            parallel.addVibrator(id, oneShot(amplitude))
        }
        return parallel.combine()
    }

    /** The attributes of Android 13 and later: media usage. */
    fun media(): VibrationAttributes =
        VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_MEDIA).build()

    /** The attributes before Android 13: the handler sets no usage. */
    fun unspecified(): VibrationAttributes = VibrationAttributes.Builder().build()
}
