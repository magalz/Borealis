package com.limelight.ui

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.SurfaceView

class StreamView : SurfaceView {
    private var desiredAspectRatio = 0.0
    private var inputCallbacks: InputCallbacks? = null

    fun setDesiredAspectRatio(aspectRatio: Double) {
        this.desiredAspectRatio = aspectRatio
    }

    fun setInputCallbacks(callbacks: InputCallbacks?) {
        this.inputCallbacks = callbacks
    }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
    ) : super(context, attrs, defStyleAttr)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int,
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // If no fixed aspect ratio has been provided, simply use the default onMeasure() behavior
        if (desiredAspectRatio == 0.0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        // Based on code from:
        // https://www.buzzingandroid.com/2012/11/easy-measuring-of-custom-views-with-specific-aspect-ratio/
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val measuredHeight: Int
        val measuredWidth: Int
        if (widthSize > heightSize * desiredAspectRatio) {
            measuredHeight = heightSize
            measuredWidth = (measuredHeight * desiredAspectRatio).toInt()
        } else {
            measuredWidth = widthSize
            measuredHeight = (measuredWidth / desiredAspectRatio).toInt()
        }

        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        // This callbacks allows us to override dumb IME behavior like when
        // Samsung's default keyboard consumes Shift+Space.
        val callbacks = inputCallbacks
        val handled =
            callbacks != null &&
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> callbacks.handleKeyDown(event)
                    KeyEvent.ACTION_UP -> callbacks.handleKeyUp(event)
                    else -> false
                }

        return handled || super.onKeyPreIme(keyCode, event)
    }

    interface InputCallbacks {
        fun handleKeyUp(event: KeyEvent): Boolean

        fun handleKeyDown(event: KeyEvent): Boolean
    }
}
