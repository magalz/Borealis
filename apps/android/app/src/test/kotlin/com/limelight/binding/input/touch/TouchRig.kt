package com.limelight.binding.input.touch

import android.app.Application
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.input.MouseButtonPacket
import com.limelight.preferences.PreferenceConfiguration
import io.github.magalz.borealis.connection.BridgeCall
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import java.util.concurrent.TimeUnit
import org.robolectric.shadows.ShadowLooper

/**
 * What a touch context needs to run in a unit test: a connection whose calls into `MoonBridge` are
 * recorded by [ShadowRecordingBridge] (load it with `@Config(shadows = ...)`), a stream view of a
 * given size, the settings, and the main looper that the contexts post their timers to.
 *
 * Each call into the bridge reads as one line (see [sent]), so a test shows the exact sequence the
 * host would see.
 */
internal class TouchRig(width: Int = HD_WIDTH, height: Int = HD_HEIGHT) {
    private val application: Application = ApplicationProvider.getApplicationContext()

    val connection = NvConnection(application, null, 0, "test", null, null, null)
    val view = View(application)
    val prefs = PreferenceConfiguration()

    init {
        ShadowRecordingBridge.reset()
        resize(width, height)
    }

    /** Gives the stream view the size that the contexts read when they scale a touch. */
    fun resize(width: Int, height: Int) = view.layout(0, 0, width, height)

    /** The calls that reached `MoonBridge` so far, oldest first. */
    fun sent(): List<String> = ShadowRecordingBridge.calls.map { it.describe() }

    /** Forgets the calls so far, so the next [sent] holds only what follows. */
    fun forget() = ShadowRecordingBridge.calls.clear()

    /** Lets [millis] of the main looper's time pass, running the timers that fall due. */
    fun advance(millis: Long) = ShadowLooper.idleMainLooper(millis, TimeUnit.MILLISECONDS)

    fun relative(actionIndex: Int = 0) =
        RelativeTouchContext(connection, actionIndex, HD_WIDTH, HD_HEIGHT, view, prefs)

    fun absolute(actionIndex: Int = 0) = AbsoluteTouchContext(connection, actionIndex, view)

    companion object {
        /** The reference screen of the trackpad (`TouchContexts`). */
        const val HD_WIDTH = 1280
        const val HD_HEIGHT = 720
    }
}

private fun buttonName(button: Any?): String =
    when (button) {
        MouseButtonPacket.BUTTON_LEFT -> "LEFT"
        MouseButtonPacket.BUTTON_MIDDLE -> "MIDDLE"
        MouseButtonPacket.BUTTON_RIGHT -> "RIGHT"
        MouseButtonPacket.BUTTON_X1 -> "X1"
        MouseButtonPacket.BUTTON_X2 -> "X2"
        else -> "button $button"
    }

/**
 * One line per call: `down RIGHT`, `up LEFT`, or the name of the bridge call with its arguments
 * (`sendMouseMove(3, -4)`). The shorts print as the numbers they hold.
 */
private fun BridgeCall.describe(): String =
    when {
        name == "sendMouseButton" && args[0] == MouseButtonPacket.PRESS_EVENT ->
            "down ${buttonName(args[1])}"
        name == "sendMouseButton" && args[0] == MouseButtonPacket.RELEASE_EVENT ->
            "up ${buttonName(args[1])}"
        else -> "$name${args.joinToString(", ", "(", ")")}"
    }

/** The time, in ms, at which the first touch of a test goes down. */
internal const val T0 = 10_000L

/** Puts a finger down on the context as `Game` does: the pointer count first, then the touch. */
internal fun RelativeTouchContext.start(x: Int, y: Int, time: Long = T0) {
    setPointerCount(1)
    assertThat(touchDownEvent(x, y, time, true)).isTrue()
}

internal fun RelativeTouchContext.moveTo(x: Int, y: Int, time: Long = T0) {
    assertThat(touchMoveEvent(x, y, time)).isTrue()
}

internal fun AbsoluteTouchContext.start(x: Int, y: Int, time: Long = T0) {
    assertThat(touchDownEvent(x, y, time, true)).isTrue()
}

internal fun AbsoluteTouchContext.moveTo(x: Int, y: Int, time: Long = T0) {
    assertThat(touchMoveEvent(x, y, time)).isTrue()
}

/** What `Game` does before every touch down and every lift: all the contexts get the count. */
internal fun setPointerCount(count: Int, vararg contexts: TouchContext) = contexts.forEach {
    it.setPointerCount(count)
}

/** A first finger down, then a second finger down: the touches of a two-finger gesture. */
internal fun twoFingers(first: RelativeTouchContext, second: RelativeTouchContext) {
    first.start(100, 100)
    setPointerCount(2, first, second)
    assertThat(second.touchDownEvent(300, 100, T0 + 10, true)).isTrue()
}
