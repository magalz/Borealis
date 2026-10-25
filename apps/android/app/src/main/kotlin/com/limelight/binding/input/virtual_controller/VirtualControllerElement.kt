/** Created by Karim Mreisi. */
package com.limelight.binding.input.virtual_controller

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import org.json.JSONException
import org.json.JSONObject

// Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
@Suppress("TooManyFunctions")
abstract class VirtualControllerElement
protected constructor(
    protected val virtualController: VirtualController,
    context: Context,
    // The layout loader saves and loads the elements by this id, from this package.
    internal val elementId: Int,
) : View(context) {
    private val paint = Paint()

    private var normalColor = NORMAL_COLOR
    protected var pressedColor = PRESSED_COLOR

    private var startSizeX = 0
    private var startSizeY = 0

    private var positionPressedX = 0f
    private var positionPressedY = 0f

    private enum class Mode {
        Normal,
        Resize,
        Move,
    }

    private var currentMode = Mode.Normal

    private fun moveElement(pressedX: Int, pressedY: Int, fingerX: Int, fingerY: Int) {
        val newPosX = this.x.toInt() + fingerX - pressedX
        val newPosY = this.y.toInt() + fingerY - pressedY

        val layoutParams = this.layoutParams as FrameLayout.LayoutParams

        layoutParams.leftMargin = if (newPosX > 0) newPosX else 0
        layoutParams.topMargin = if (newPosY > 0) newPosY else 0
        layoutParams.rightMargin = 0
        layoutParams.bottomMargin = 0

        requestLayout()
    }

    private fun resizeElement(pressedX: Int, pressedY: Int, fingerX: Int, fingerY: Int) {
        val layoutParams = this.layoutParams as FrameLayout.LayoutParams

        val newHeight = fingerY + (startSizeY - pressedY)
        val newWidth = fingerX + (startSizeX - pressedX)

        layoutParams.height = if (newHeight > MIN_SIZE) newHeight else MIN_SIZE
        layoutParams.width = if (newWidth > MIN_SIZE) newWidth else MIN_SIZE

        requestLayout()
    }

    override fun onDraw(canvas: Canvas) {
        onElementDraw(canvas)

        if (currentMode != Mode.Normal) {
            paint.color = CONFIG_SELECTED_COLOR
            paint.strokeWidth = defaultStrokeWidth.toFloat()
            paint.style = Paint.Style.STROKE

            canvas.drawRect(
                paint.strokeWidth,
                paint.strokeWidth,
                width - paint.strokeWidth,
                height - paint.strokeWidth,
                paint,
            )
        }

        super.onDraw(canvas)
    }

    private fun actionEnableMove() {
        currentMode = Mode.Move
    }

    private fun actionEnableResize() {
        currentMode = Mode.Resize
    }

    private fun actionCancel() {
        currentMode = Mode.Normal
        invalidate()
    }

    protected val defaultColor: Int
        get() =
            when (virtualController.controllerMode) {
                VirtualController.ControllerMode.MoveButtons -> CONFIG_MOVE_COLOR
                VirtualController.ControllerMode.ResizeButtons -> CONFIG_RESIZE_COLOR
                else -> normalColor
            }

    protected val defaultStrokeWidth: Int
        get() {
            val screen = resources.displayMetrics
            return (screen.heightPixels * STROKE_RATIO).toInt()
        }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Ignore secondary touches on controls
        //
        // NB: We can get an additional pointer down if the user touches a non-StreamView area
        // while also touching an OSC control, even if that pointer down doesn't correspond to
        // an area of the OSC control.
        if (event.actionIndex != 0) {
            return true
        }

        if (virtualController.controllerMode == VirtualController.ControllerMode.Active) {
            return onElementTouchEvent(event)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                positionPressedX = event.x
                positionPressedY = event.y
                startSizeX = width
                startSizeY = height

                if (
                    virtualController.controllerMode == VirtualController.ControllerMode.MoveButtons
                ) {
                    actionEnableMove()
                } else if (
                    virtualController.controllerMode ==
                        VirtualController.ControllerMode.ResizeButtons
                ) {
                    actionEnableResize()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                when (currentMode) {
                    Mode.Move ->
                        moveElement(
                            positionPressedX.toInt(),
                            positionPressedY.toInt(),
                            event.x.toInt(),
                            event.y.toInt(),
                        )
                    Mode.Resize ->
                        resizeElement(
                            positionPressedX.toInt(),
                            positionPressedY.toInt(),
                            event.x.toInt(),
                            event.y.toInt(),
                        )
                    Mode.Normal -> Unit
                }
            }
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_UP -> actionCancel()
            else -> Unit
        }
        return true
    }

    protected abstract fun onElementDraw(canvas: Canvas)

    abstract fun onElementTouchEvent(event: MotionEvent): Boolean

    fun setOpacity(opacity: Int) {
        val hexOpacity = opacity * MAX_ALPHA / PERCENT_SCALE
        normalColor = (hexOpacity shl ALPHA_SHIFT) or (normalColor and RGB_MASK)
        pressedColor = (hexOpacity shl ALPHA_SHIFT) or (pressedColor and RGB_MASK)

        invalidate()
    }

    protected fun getPercent(value: Float, percent: Float): Float = value / PERCENT * percent

    protected val correctWidth: Int
        get() = if (width > height) height else width

    @Throws(JSONException::class)
    open fun getConfiguration(): JSONObject {
        val configuration = JSONObject()

        val layoutParams = this.layoutParams as FrameLayout.LayoutParams

        configuration.put("LEFT", layoutParams.leftMargin)
        configuration.put("TOP", layoutParams.topMargin)
        configuration.put("WIDTH", layoutParams.width)
        configuration.put("HEIGHT", layoutParams.height)

        return configuration
    }

    @Throws(JSONException::class)
    fun loadConfiguration(configuration: JSONObject) {
        val layoutParams = this.layoutParams as FrameLayout.LayoutParams

        layoutParams.leftMargin = configuration.getInt("LEFT")
        layoutParams.topMargin = configuration.getInt("TOP")
        layoutParams.width = configuration.getInt("WIDTH")
        layoutParams.height = configuration.getInt("HEIGHT")

        requestLayout()
    }

    companion object {
        const val EID_DPAD = 1
        const val EID_LT = 2
        const val EID_RT = 3
        const val EID_LB = 4
        const val EID_RB = 5
        const val EID_A = 6
        const val EID_B = 7
        const val EID_X = 8
        const val EID_Y = 9
        const val EID_BACK = 10
        const val EID_START = 11
        const val EID_LS = 12
        const val EID_RS = 13
        const val EID_LSB = 14
        const val EID_RSB = 15
        const val EID_GDB = 16

        private const val NORMAL_COLOR = 0xF0888888.toInt()
        private const val PRESSED_COLOR = 0xF00000FF.toInt()
        private const val CONFIG_MOVE_COLOR = 0xF0FF0000.toInt()
        private const val CONFIG_RESIZE_COLOR = 0xF0FF00FF.toInt()
        private const val CONFIG_SELECTED_COLOR = 0xF000FF00.toInt()

        // The smallest width and height that resizing leaves an element, in pixels
        private const val MIN_SIZE = 20

        // The stroke is this share of the screen height, cut to whole pixels
        private const val STROKE_RATIO = 0.004f

        private const val MAX_ALPHA = 255
        private const val PERCENT_SCALE = 100
        private const val ALPHA_SHIFT = 24
        private const val RGB_MASK = 0x00FFFFFF
        private const val PERCENT = 100f
    }
}
