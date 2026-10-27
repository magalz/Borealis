package io.github.magalz.borealis.stream.game.motion

import android.view.InputDevice
import android.view.MotionEvent
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.InputDeviceBuilder
import org.robolectric.shadows.ShadowInputEvent

/** The motion events that the stream activity reads, as the framework hands them to a view. */
internal object Axes {
    const val X = MotionEvent.AXIS_X
    const val Y = MotionEvent.AXIS_Y
    const val PRESSURE = MotionEvent.AXIS_PRESSURE
    const val TOUCH_MAJOR = MotionEvent.AXIS_TOUCH_MAJOR
    const val TOUCH_MINOR = MotionEvent.AXIS_TOUCH_MINOR
    const val TOOL_MAJOR = MotionEvent.AXIS_TOOL_MAJOR
    const val TOOL_MINOR = MotionEvent.AXIS_TOOL_MINOR
    const val ORIENTATION = MotionEvent.AXIS_ORIENTATION
    const val TILT = MotionEvent.AXIS_TILT
    const val DISTANCE = MotionEvent.AXIS_DISTANCE
    const val VSCROLL = MotionEvent.AXIS_VSCROLL
    const val HSCROLL = MotionEvent.AXIS_HSCROLL
    const val RELATIVE_X = MotionEvent.AXIS_RELATIVE_X
    const val RELATIVE_Y = MotionEvent.AXIS_RELATIVE_Y
}

/** One pointer of an event: its tool, its position and the other axes that it carries. */
internal class Pointer(
    val toolType: Int,
    val x: Float,
    val y: Float,
    val axes: Map<Int, Float> = emptyMap(),
) {
    fun at(x: Float, y: Float) = Pointer(toolType, x, y, axes)
}

/** The state of a motion event that a test chooses; everything else has a neutral value. */
internal class MotionSpec(val source: Int, val action: Int) {
    /** The pointers of the current sample; the order is the order of the event. */
    val pointers = mutableListOf<Pointer>()

    /** The samples older than the current one, the oldest first: the history of the event. */
    val history = mutableListOf<List<Pointer>>()

    /** Which pointer the action is for; it goes into the action as the framework puts it. */
    var actionIndex = 0
    var buttonState = 0
    var flags = 0
    var eventTime = EVENT_TIME

    /** What `getDevice()` answers; the event has the id of that device. Null: no device, id 0. */
    var device: InputDevice? = null

    /** The pointer's tool is `toolType`; [axes] are the other axes that the pointer reports. */
    fun pointer(
        x: Float,
        y: Float,
        toolType: Int = MotionEvent.TOOL_TYPE_MOUSE,
        axes: Map<Int, Float> = emptyMap(),
    ) {
        pointers += Pointer(toolType, x, y, axes)
    }

    /**
     * An older sample: the pointers of the event, in order, at [positions]. Samples are added from
     * the oldest to the newest, and the pointers of the event are its newest sample.
     */
    fun earlier(vararg positions: Pair<Float, Float>) {
        check(positions.size == pointers.size) { "one position for each pointer" }
        history += pointers.zip(positions) { pointer, (x, y) -> pointer.at(x, y) }
    }

    companion object {
        const val EVENT_TIME = 10_000L
    }
}

private fun coordinatesOf(pointer: Pointer) =
    MotionEvent.PointerCoords().apply {
        setAxisValue(Axes.X, pointer.x)
        setAxisValue(Axes.Y, pointer.y)
        pointer.axes.forEach { (axis, value) -> setAxisValue(axis, value) }
    }

/**
 * A motion event of [source] with [action], built by [build]. The pointers have the ids 0, 1 and so
 * on, in order. The history is the samples that [MotionSpec.earlier] adds, batched on the event:
 * the samples are one millisecond apart and the newest has the time [MotionSpec.eventTime].
 */
internal fun motionEventOf(
    source: Int,
    action: Int,
    build: MotionSpec.() -> Unit = {},
): MotionEvent {
    val spec = MotionSpec(source, action).apply(build)
    check(spec.pointers.isNotEmpty()) { "the event has no pointer" }
    val properties =
        spec.pointers
            .mapIndexed { index, pointer ->
                MotionEvent.PointerProperties().apply {
                    id = index
                    toolType = pointer.toolType
                }
            }
            .toTypedArray()
    // The first sample is what the event is made with; the later ones are batched on it. The last
    // sample is the current value, so the history comes first.
    val samples = spec.history + listOf(spec.pointers)
    val firstTime = spec.eventTime - (samples.size - 1)
    val encodedAction = spec.action or (spec.actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
    val event =
        MotionEvent.obtain(
            firstTime,
            firstTime,
            encodedAction,
            spec.pointers.size,
            properties,
            samples.first().map(::coordinatesOf).toTypedArray(),
            0,
            spec.buttonState,
            1f,
            1f,
            spec.device?.id ?: 0,
            0,
            spec.source,
            spec.flags,
        )
    samples.drop(1).forEachIndexed { index, sample ->
        event.addBatch(firstTime + 1 + index, sample.map(::coordinatesOf).toTypedArray(), 0)
    }
    spec.device?.let { Shadow.extract<ShadowInputEvent>(event).setDevice(it) }
    return event
}

/** A motion range of an input device: the [axis] as seen from [source], from [min] to [max]. */
internal class Range(val axis: Int, val source: Int, val min: Float, val max: Float)

/**
 * An input device with the [sources] and the motion [ranges] that the stream activity asks the
 * device for. The device has the id [id].
 */
internal fun inputDeviceOf(id: Int, sources: Int, vararg ranges: Range): InputDevice {
    val builder =
        InputDeviceBuilder.newBuilder().setId(id).setName("motion-$id").setSources(sources)
    for (range in ranges) {
        builder.addMotionRange(range.axis, range.source, range.min, range.max, 0f, 0f, 0f)
    }
    return builder.build()
}
