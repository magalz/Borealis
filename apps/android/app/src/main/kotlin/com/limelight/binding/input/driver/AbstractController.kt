package com.limelight.binding.input.driver

abstract class AbstractController(
    deviceId: Int,
    private val listener: UsbDriverListener,
    val vendorId: Int,
    val productId: Int,
) {
    val controllerId: Int = deviceId

    @JvmField protected var buttonFlags: Int = 0

    // The buttons the pad has, which the controller handler reports to the host in the arrival.
    // The Xbox drivers set it; the wireless dongle is no pad and leaves it 0.
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

    /**
     * Sets the two main motors. Returns true when the write went through, and false when the
     * transfer ended with anything but the whole packet (a timeout, a failure or a short write). A
     * pad with nothing to write returns true: nothing failed.
     */
    abstract fun rumble(lowFreqMotor: Short, highFreqMotor: Short): Boolean

    /** Sets the trigger motors. The result means what the result of [rumble] means. */
    abstract fun rumbleTriggers(leftTrigger: Short, rightTrigger: Short): Boolean

    protected fun notifyDeviceRemoved() {
        listener.deviceRemoved(this)
    }

    protected fun notifyDeviceAdded() {
        listener.deviceAdded(this)
    }
}
