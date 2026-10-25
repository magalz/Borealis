package com.limelight.binding.input.driver

abstract class AbstractController(
    deviceId: Int,
    private val listener: UsbDriverListener,
    val vendorId: Int,
    val productId: Int,
) {
    val controllerId: Int = deviceId

    @JvmField protected var buttonFlags: Int = 0

    // Never assigned, as in the inherited Java: the controller handler reports 0 to the host
    // (Sprint 13 backlog).
    var supportedButtonFlags: Int = 0
        protected set

    @JvmField protected var leftTrigger: Float = 0f
    @JvmField protected var rightTrigger: Float = 0f
    @JvmField protected var rightStickX: Float = 0f
    @JvmField protected var rightStickY: Float = 0f
    @JvmField protected var leftStickX: Float = 0f
    @JvmField protected var leftStickY: Float = 0f

    var capabilities: Short = 0
        protected set

    var type: Byte = 0
        protected set

    protected fun setButtonFlag(buttonFlag: Int, data: Int) {
        if (data != 0) {
            buttonFlags = buttonFlags or buttonFlag
        } else {
            buttonFlags = buttonFlags and buttonFlag.inv()
        }
    }

    protected fun reportInput() {
        listener.reportControllerState(
            controllerId,
            buttonFlags,
            leftStickX,
            leftStickY,
            rightStickX,
            rightStickY,
            leftTrigger,
            rightTrigger,
        )
    }

    abstract fun start(): Boolean

    abstract fun stop()

    abstract fun rumble(lowFreqMotor: Short, highFreqMotor: Short)

    abstract fun rumbleTriggers(leftTrigger: Short, rightTrigger: Short)

    protected fun notifyDeviceRemoved() {
        listener.deviceRemoved(this)
    }

    protected fun notifyDeviceAdded() {
        listener.deviceAdded(this)
    }
}
