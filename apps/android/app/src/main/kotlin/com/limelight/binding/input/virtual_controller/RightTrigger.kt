/** Created by Karim Mreisi. */
package com.limelight.binding.input.virtual_controller

import android.annotation.SuppressLint
import android.content.Context

// ViewConstructor lint: the elements have no (Context) constructor on purpose, they need their
// controller and their id (Sprint 18c backlog).
@SuppressLint("ViewConstructor")
class RightTrigger(controller: VirtualController, layer: Int, context: Context) :
    DigitalButton(controller, EID_RT, layer, context) {
    init {
        addDigitalButtonListener(
            object : DigitalButtonListener {
                override fun onClick() {
                    val inputContext = controller.controllerInputContext
                    inputContext.rightTrigger = TRIGGER_PRESSED

                    controller.sendControllerInputContext()
                }

                override fun onRelease() {
                    val inputContext = controller.controllerInputContext
                    inputContext.rightTrigger = TRIGGER_RELEASED

                    controller.sendControllerInputContext()
                }
            }
        )
    }

    private companion object {
        // A trigger is fully pressed at 0xFF, which is -1 as a byte, and released at 0
        private const val TRIGGER_PRESSED = 0xFF.toByte()
        private const val TRIGGER_RELEASED = 0x00.toByte()
    }
}
