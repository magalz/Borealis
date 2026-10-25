package com.limelight.binding.input.virtual_controller

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import android.preference.PreferenceManager
import android.view.MotionEvent
import android.view.View.MeasureSpec
import android.widget.Button
import android.widget.FrameLayout
import com.limelight.nvstream.input.ControllerPacket
import io.github.magalz.borealis.input.ControllerHandlerRig
import org.json.JSONObject
import org.robolectric.Shadows.shadowOf

/** A screen in pixels: width and height of the display, in landscape. */
data class Screen(val width: Int, val height: Int)

/** The place of the element with the id [id]: the four numbers that the saved layout holds. */
data class Placed(val id: Int, val left: Int, val top: Int, val width: Int, val height: Int)

/**
 * The id of an element, which the layout saves it under. `elementId` is protected in the element,
 * and a view's own `id` would hide an extension of that name.
 */
val VirtualControllerElement.eid: Int
    get() = elementId

/** Where the element sits in its frame, from the layout parameters the controller gave it. */
fun VirtualControllerElement.placed(): Placed {
    val configuration = getConfiguration()
    return Placed(
        eid,
        configuration.getInt("LEFT"),
        configuration.getInt("TOP"),
        configuration.getInt("WIDTH"),
        configuration.getInt("HEIGHT"),
    )
}

/** The text a button shows; the button keeps it private, and it is drawn only. */
fun DigitalButton.label(): String = privateField("text") as String

/** The drawable id a button shows instead of its text, or -1; the button keeps it private. */
fun DigitalButton.icon(): Int = privateField("icon") as Int

private fun DigitalButton.privateField(name: String): Any? =
    DigitalButton::class.java.getDeclaredField(name).apply { isAccessible = true }.get(this)

// The keys of the settings, which the default layout reads (see `PreferenceConfiguration`).
private const val ONLY_L3_R3_KEY = "checkbox_only_show_L3R3"
private const val FLIP_FACE_BUTTONS_KEY = "checkbox_flip_face_buttons"
private const val SHOW_GUIDE_BUTTON_KEY = "checkbox_show_guide_button"
private const val OPACITY_KEY = "seekbar_osc_opacity"

/**
 * A [VirtualController] on a [FrameLayout] of an activity of the given [screen], over a real
 * `ControllerHandler` that hands what it is told to a recording connection.
 *
 * The controller reads the screen from the activity's display metrics and the layout choices from
 * the default preferences, which [settings] writes; the layout comes from `refreshLayout`.
 */
class OscRig(screen: Screen = PHONE, withHandler: Boolean = true) {
    val handlerRig = ControllerHandlerRig()
    val activity: Activity = handlerRig.activity
    val frame = FrameLayout(activity)
    val controller = VirtualController(if (withHandler) handlerRig.handler else null, frame, activity)

    /** What reached the PC from the controller: the packets of the recording connection. */
    val packets
        get() = handlerRig.connection.packets

    init {
        screen(screen)
    }

    /** Resizes the display the controller reads. */
    fun screen(screen: Screen) {
        activity.resources.displayMetrics.apply {
            widthPixels = screen.width
            heightPixels = screen.height
        }
    }

    /** Writes the settings of the on-screen controller; the layout reads them when it is built. */
    @Suppress("DEPRECATION") // The app reads its settings from the default preferences of this API.
    fun settings(
        onlyL3R3: Boolean = false,
        flipFaceButtons: Boolean = false,
        showGuideButton: Boolean = true,
        opacity: Int? = null,
    ) {
        PreferenceManager.getDefaultSharedPreferences(activity)
            .edit()
            .putBoolean(ONLY_L3_R3_KEY, onlyL3R3)
            .putBoolean(FLIP_FACE_BUTTONS_KEY, flipFaceButtons)
            .putBoolean(SHOW_GUIDE_BUTTON_KEY, showGuideButton)
            .apply { opacity?.let { putInt(OPACITY_KEY, it) } }
            .commit()
    }

    /** The preference file that holds the saved layout, `OSC`. */
    val saved: SharedPreferences
        get() = activity.getSharedPreferences("OSC", Context.MODE_PRIVATE)

    /** The saved layout as the settings screen of a user would find it: the JSON of each id. */
    fun savedLayout(): Map<String, Placed> =
        saved.all.mapValues { (key, json) ->
            val configuration = JSONObject(json as String)
            Placed(
                key.toInt(),
                configuration.getInt("LEFT"),
                configuration.getInt("TOP"),
                configuration.getInt("WIDTH"),
                configuration.getInt("HEIGHT"),
            )
        }

    /** The elements the controller holds, in the order it added them. */
    fun layout(): List<Placed> = controller.elements.map { it.placed() }

    fun element(id: Int): VirtualControllerElement = controller.elements.first { it.eid == id }

    /** The configuration button that `refreshLayout` adds to the frame before the elements. */
    val configureButton: Button
        get() = frame.getChildAt(0) as Button

    /** Gives every view its size, as the frame layout would on the screen: needed to touch them. */
    fun layOut() {
        val metrics = activity.resources.displayMetrics
        frame.measure(
            MeasureSpec.makeMeasureSpec(metrics.widthPixels, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(metrics.heightPixels, MeasureSpec.EXACTLY),
        )
        frame.layout(0, 0, metrics.widthPixels, metrics.heightPixels)
    }

    /** Moves the main looper's clock, which the controller's delayed work follows. */
    fun advanceBy(millis: Long) = handlerRig.advanceBy(millis)

    /** Touches the element at ([x], [y]) in its own coordinates. */
    fun touch(element: VirtualControllerElement, action: Int, x: Float = 1f, y: Float = 1f) {
        val now = handlerRig.now
        val event = MotionEvent.obtain(now, now, action, x, y, 0)
        element.onTouchEvent(event)
        event.recycle()
    }

    fun press(element: VirtualControllerElement, x: Float = 1f, y: Float = 1f) =
        touch(element, MotionEvent.ACTION_DOWN, x, y)

    fun release(element: VirtualControllerElement, x: Float = 1f, y: Float = 1f) =
        touch(element, MotionEvent.ACTION_UP, x, y)

    /** What the main looper has scheduled for the controller: false when nothing is pending. */
    fun hasScheduledWork(): Boolean = !shadowOf(Looper.getMainLooper()).isIdle

    /** The buttons of the last packet sent, as the handler gave them to the connection. */
    fun lastButtons(): Int? = packets.lastOrNull()?.buttons

    companion object {
        /** A phone in landscape: 2340 by 1080, wider than 16:9. */
        val PHONE = Screen(2340, 1080)

        /** A TV at 1920 by 1080, which is 16:9. */
        val TV = Screen(1920, 1080)

        /** A 4:3 tablet, narrower than 16:9, so the right controls are displaced to the left. */
        val TABLET_4_3 = Screen(2048, 1536)

        /** A 16:10 tablet, with a height that does not divide by the 72 rows of the grid. */
        val TABLET_16_10 = Screen(2560, 1600)

        /**
         * A 1920 by 1200 panel, 16:10. At this height the order of the float operations of the grid
         * shows: dividing by 72 first and multiplying after places the pad at 499, where
         * multiplying first would place it at 500.
         */
        val WUXGA = Screen(1920, 1200)

        const val A = ControllerPacket.A_FLAG
        const val B = ControllerPacket.B_FLAG
        const val X = ControllerPacket.X_FLAG
        const val Y = ControllerPacket.Y_FLAG
    }
}
