/** Created by Karim Mreisi. */
package com.limelight.binding.input.virtual_controller

import android.annotation.SuppressLint
import android.content.Context

// Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
// The elements have no (Context) constructor on purpose: they need their controller and their id.
@SuppressLint("ViewConstructor")
class LeftTrigger(controller: VirtualController, layer: Int, context: Context) :
    DigitalButton(controller, EID_LT, layer, context) {
    init {
        addDigitalButtonListener(
            object : DigitalButtonListener {
                override fun onClick() {
                    val inputContext = controller.controllerInputContext
                    inputContext.leftTrigger = TRIGGER_PRESSED

                    controller.sendControllerInputContext()
                }

                override fun onLongClick() = Unit

                override fun onRelease() {
                    val inputContext = controller.controllerInputContext
                    inputContext.leftTrigger = TRIGGER_RELEASED

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
