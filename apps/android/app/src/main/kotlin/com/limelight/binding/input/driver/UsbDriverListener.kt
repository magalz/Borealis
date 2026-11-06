package com.limelight.binding.input.driver

// Converted as is from the inherited Java; LongParameterList stays because the interface is frozen:
// ControllerHandler and UsbDriverService implement it (Sprint 18c backlog).
@Suppress("LongParameterList")
interface UsbDriverListener {
    fun reportControllerState(
        controllerId: Int,
        buttonFlags: Int,
        leftStickX: Float,
        leftStickY: Float,
        rightStickX: Float,
        rightStickY: Float,
        leftTrigger: Float,
        rightTrigger: Float,
    )

    fun deviceRemoved(controller: AbstractController)

    fun deviceAdded(controller: AbstractController)
}
