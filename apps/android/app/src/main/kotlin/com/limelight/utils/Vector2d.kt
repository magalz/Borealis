package com.limelight.utils

class Vector2d {
    private var xValue = 0f
    private var yValue = 0f

    var magnitude = 0.0
        private set

    // A setter initializes the vector again, so that the magnitude follows.
    var x: Float
        get() = xValue
        set(value) = initialize(value, yValue)

    var y: Float
        get() = yValue
        set(value) = initialize(xValue, value)

    init {
        initialize(0f, 0f)
    }

    fun initialize(x: Float, y: Float) {
        xValue = x
        yValue = y
        magnitude = Math.sqrt(Math.pow(x.toDouble(), 2.0) + Math.pow(y.toDouble(), 2.0))
    }

    fun getNormalized(vector: Vector2d) {
        vector.initialize((xValue / magnitude).toFloat(), (yValue / magnitude).toFloat())
    }

    fun scalarMultiply(factor: Double) {
        initialize((xValue * factor).toFloat(), (yValue * factor).toFloat())
    }

    companion object {
        @JvmField val ZERO = Vector2d()
    }
}
