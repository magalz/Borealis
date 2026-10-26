package io.github.magalz.borealis.input

import android.app.Activity
import android.content.Context
import android.view.InputDevice
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.View
import com.limelight.R
import com.limelight.binding.input.capture.AndroidNativePointerCaptureProvider
import io.github.magalz.borealis.designsystem.testing.connect
import io.github.magalz.borealis.designsystem.testing.disconnect
import io.github.magalz.borealis.designsystem.testing.inputDevice
import io.github.magalz.borealis.designsystem.testing.inputDeviceListeners
import java.util.concurrent.TimeUnit
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper

/**
 * A view that records what is asked of its pointer capture. Robolectric does not shadow pointer
 * capture (the real view never has it, whatever is asked), so the provider is given this one. It
 * answers [hasPointerCapture] with [hasCapture], which a test sets.
 */
internal class PointerCaptureView(context: Context) : View(context) {
    var requests = 0
        private set

    var releases = 0
        private set

    var hasCapture = false

    /** Runs when the capture is released, before the release is counted. */
    var onRelease: () -> Unit = {}

    /** Runs when the capture is requested, before the request is counted. */
    var onRequest: () -> Unit = {}

    override fun requestPointerCapture() {
        onRequest()
        requests++
    }

    override fun releasePointerCapture() {
        onRelease()
        releases++
    }

    override fun hasPointerCapture(): Boolean = hasCapture
}

/**
 * A game activity as far as the mouse capture goes: an activity with the stream view, which is
 * where `Game` puts the capture provider, and a provider on that view.
 */
internal class CaptureRig {
    val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    val view = PointerCaptureView(activity)

    init {
        view.id = R.id.surfaceView
        activity.setContentView(view)
    }

    val provider = AndroidNativePointerCaptureProvider(activity, view)

    /** The icon that hides the cursor. */
    val nullIcon: PointerIcon = PointerIcon.getSystemIcon(activity, PointerIcon.TYPE_NULL)

    /** Makes a device with these sources one of the devices that Android has. */
    fun connect(id: Int, sources: Int) = connect(activity, inputDevice(id, sources))

    fun disconnect(id: Int) = disconnect(activity, id)

    /** ChromeOS runs the app in its Android container, which says so with this feature. */
    fun onChromeOs() = shadowOf(activity.packageManager).setSystemFeature(CHROME_OS_FEATURE, true)

    /** Whether the provider is registered with the input manager, which tells it about devices. */
    fun listening(): Boolean = inputDeviceListeners().any { it === provider }

    /** Moves the clock and runs what the main looper has scheduled in that time. */
    fun advanceBy(millis: Long) {
        ShadowLooper.idleMainLooper(millis, TimeUnit.MILLISECONDS)
    }

    companion object {
        const val CHROME_OS_FEATURE = "org.chromium.arc.device_management"
    }
}

/**
 * A pointer event with one pointer. [samples] go from the oldest to the newest: the newest is the
 * current value of the event and the ones before it are its history, each an axis to its value.
 */
internal fun motionEvent(
    source: Int,
    samples: List<Map<Int, Float>>,
    toolType: Int = MotionEvent.TOOL_TYPE_MOUSE,
): MotionEvent {
    val properties =
        MotionEvent.PointerProperties().apply {
            id = 0
            this.toolType = toolType
        }

    fun coords(axes: Map<Int, Float>) =
        MotionEvent.PointerCoords().apply { axes.forEach { (axis, value) -> setAxisValue(axis, value) } }

    val event =
        MotionEvent.obtain(
            0L,
            0L,
            MotionEvent.ACTION_MOVE,
            1,
            arrayOf(properties),
            arrayOf(coords(samples.first())),
            0,
            0,
            1f,
            1f,
            0,
            0,
            source,
            0,
        )
    samples.drop(1).forEachIndexed { index, sample ->
        event.addBatch(index + 1L, arrayOf(coords(sample)), 0)
    }
    return event
}

internal fun motionEvent(
    source: Int,
    axes: Map<Int, Float>,
    toolType: Int = MotionEvent.TOOL_TYPE_MOUSE,
): MotionEvent = motionEvent(source, listOf(axes), toolType)

/** The sources that the capture providers look at, spelled as the framework spells them. */
internal object Sources {
    const val MOUSE = InputDevice.SOURCE_MOUSE
    const val MOUSE_RELATIVE = InputDevice.SOURCE_MOUSE_RELATIVE
    const val TOUCHPAD = InputDevice.SOURCE_TOUCHPAD
    const val TOUCHSCREEN = InputDevice.SOURCE_TOUCHSCREEN
    const val KEYBOARD = InputDevice.SOURCE_KEYBOARD
    const val GAMEPAD = InputDevice.SOURCE_GAMEPAD
}
