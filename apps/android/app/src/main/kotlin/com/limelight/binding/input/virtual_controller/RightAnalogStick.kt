/** Created by Karim Mreisi. */
package com.limelight.binding.input.virtual_controller

import android.annotation.SuppressLint
import android.content.Context
import com.limelight.nvstream.input.ControllerPacket

// Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
// The elements have no (Context) constructor on purpose: they need their controller and their id.
@SuppressLint("ViewConstructor")
class RightAnalogStick(controller: VirtualController, context: Context) :
    AnalogStick(controller, context, EID_RS) {
    init {
        addAnalogStickListener(
            object : AnalogStickListener {
                override fun onMovement(x: Float, y: Float) {
                    val inputContext = controller.controllerInputContext
                    inputContext.rightStickX = (x * STICK_RANGE).toInt().toShort()
                    inputContext.rightStickY = (y * STICK_RANGE).toInt().toShort()

                    controller.sendControllerInputContext()
                }

                override fun onClick() = Unit

                override fun onDoubleClick() {
                    val inputContext = controller.controllerInputContext
                    inputContext.inputMap =
                        (inputContext.inputMap.toInt() or ControllerPacket.RS_CLK_FLAG).toShort()

                    controller.sendControllerInputContext()
                }

                override fun onRevoke() {
                    val inputContext = controller.controllerInputContext
                    inputContext.inputMap =
                        (inputContext.inputMap.toInt() and ControllerPacket.RS_CLK_FLAG.inv())
                            .toShort()

                    controller.sendControllerInputContext()
                }
            }
        )
    }

    private companion object {
        // The largest number of a stick, which a finger at the end of the way sends
        private const val STICK_RANGE = 0x7FFE
    }
}
