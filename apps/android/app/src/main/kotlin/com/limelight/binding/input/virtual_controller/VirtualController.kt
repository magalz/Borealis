/** Created by Karim Mreisi. */
package com.limelight.binding.input.virtual_controller

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import com.limelight.R
import com.limelight.binding.input.ControllerHandler

class VirtualController(
    private val controllerHandler: ControllerHandler?,
    private val frameLayout: FrameLayout?,
    private val context: Context,
) {
    class ControllerInputContext {
        @JvmField var inputMap: Short = 0x0000
        @JvmField var leftTrigger: Byte = 0x00
        @JvmField var rightTrigger: Byte = 0x00
        @JvmField var rightStickX: Short = 0x0000
        @JvmField var rightStickY: Short = 0x0000
        @JvmField var leftStickX: Short = 0x0000
        @JvmField var leftStickY: Short = 0x0000
    }

    enum class ControllerMode {
        Active,
        MoveButtons,
        ResizeButtons,
    }

    // The elements of the layout call it from their own listeners (Kotlin, in this package).
    val handler: Handler = Handler(Looper.getMainLooper())

    private val delayedRetransmitRunnable = Runnable { sendControllerInputContextInternal() }

    private var currentMode = ControllerMode.Active

    private val buttonConfigure = Button(context)

    private val elementList = ArrayList<VirtualControllerElement>()

    val controllerInputContext = ControllerInputContext()

    val controllerMode: ControllerMode
        get() = currentMode

    val elements: List<VirtualControllerElement>
        get() = elementList

    init {
        buttonConfigure.alpha = CONFIGURE_BUTTON_ALPHA
        buttonConfigure.isFocusable = false
        buttonConfigure.setBackgroundResource(R.drawable.ic_settings)
        buttonConfigure.setOnClickListener {
            val message: String

            when (currentMode) {
                ControllerMode.Active -> {
                    currentMode = ControllerMode.MoveButtons
                    message = "Entering configuration mode (Move buttons)"
                }
                ControllerMode.MoveButtons -> {
                    currentMode = ControllerMode.ResizeButtons
                    message = "Entering configuration mode (Resize buttons)"
                }
                ControllerMode.ResizeButtons -> {
                    currentMode = ControllerMode.Active
                    VirtualControllerConfigurationLoader.saveProfile(this, context)
                    message = "Exiting configuration mode"
                }
            }

            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

            buttonConfigure.invalidate()

            for (element in elementList) {
                element.invalidate()
            }
        }
    }

    fun hide() {
        for (element in elementList) {
            element.visibility = View.INVISIBLE
        }

        buttonConfigure.visibility = View.INVISIBLE
    }

    fun show() {
        for (element in elementList) {
            element.visibility = View.VISIBLE
        }

        buttonConfigure.visibility = View.VISIBLE
    }

    fun removeElements() {
        for (element in elementList) {
            requireFrame().removeView(element)
        }
        elementList.clear()

        requireFrame().removeView(buttonConfigure)
    }

    fun setOpacity(opacity: Int) {
        for (element in elementList) {
            element.setOpacity(opacity)
        }
    }

    fun addElement(element: VirtualControllerElement, x: Int, y: Int, width: Int, height: Int) {
        elementList.add(element)
        val layoutParams = FrameLayout.LayoutParams(width, height)
        layoutParams.setMargins(x, y, 0, 0)

        requireFrame().addView(element, layoutParams)
    }

    fun refreshLayout() {
        removeElements()

        val screen = context.resources.displayMetrics

        val buttonSize = (screen.heightPixels * CONFIGURE_BUTTON_SIZE_RATIO).toInt()
        val params = FrameLayout.LayoutParams(buttonSize, buttonSize)
        params.leftMargin = CONFIGURE_BUTTON_MARGIN
        params.topMargin = CONFIGURE_BUTTON_MARGIN
        requireFrame().addView(buttonConfigure, params)

        // Start with the default layout
        VirtualControllerConfigurationLoader.createDefaultLayout(this, context)

        // Apply user preferences onto the default layout
        VirtualControllerConfigurationLoader.loadFromPreferences(this, context)
    }

    private fun sendControllerInputContextInternal() {
        controllerHandler?.reportOscState(
            controllerInputContext.inputMap.toInt(),
            controllerInputContext.leftStickX,
            controllerInputContext.leftStickY,
            controllerInputContext.rightStickX,
            controllerInputContext.rightStickY,
            controllerInputContext.leftTrigger,
            controllerInputContext.rightTrigger,
        )
    }

    // The elements of the layout call it from their own listeners (Kotlin, in this package).
    fun sendControllerInputContext() {
        // Cancel retransmissions of prior gamepad inputs
        handler.removeCallbacks(delayedRetransmitRunnable)

        sendControllerInputContextInternal()

        // HACK: GFE sometimes discards gamepad packets when they are received
        // very shortly after another. This can be critical if an axis zeroing packet
        // is lost and causes an analog stick to get stuck. To avoid this, we retransmit
        // the gamepad state a few times unless another input event happens before then.
        handler.postDelayed(delayedRetransmitRunnable, FIRST_RETRANSMIT_DELAY_MS)
        handler.postDelayed(delayedRetransmitRunnable, SECOND_RETRANSMIT_DELAY_MS)
        handler.postDelayed(delayedRetransmitRunnable, THIRD_RETRANSMIT_DELAY_MS)
    }

    // A null layout fails on its first use, as in the Java, not in the constructor.
    private fun requireFrame(): FrameLayout = checkNotNull(frameLayout)

    private companion object {
        private const val CONFIGURE_BUTTON_ALPHA = 0.25f
        private const val CONFIGURE_BUTTON_SIZE_RATIO = 0.06f
        private const val CONFIGURE_BUTTON_MARGIN = 15
        private const val FIRST_RETRANSMIT_DELAY_MS = 25L
        private const val SECOND_RETRANSMIT_DELAY_MS = 50L
        private const val THIRD_RETRANSMIT_DELAY_MS = 75L
    }
}
