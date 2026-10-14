package io.github.magalz.borealis.diagnostics.audit

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
class EraseAuditPreferenceTest {
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private var erased = 0
    private val preference =
        EraseAuditPreference(activity).apply {
            eraser = {
                erased++
            }
        }

    private fun texts(view: View): List<String> =
        when (view) {
            is TextView -> listOf(view.text.toString())
            is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
            else -> emptyList()
        }

    private fun dialog(): AlertDialog = ShadowAlertDialog.getLatestAlertDialog()

    private fun open() {
        preference.onClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun press(keyCode: Int) {
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            dialog().window!!.decorView.dispatchKeyEvent(KeyEvent(action, keyCode))
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `the row has a title and a summary without any xml attributes`() {
        assertThat(preference.title.toString()).isEqualTo("Erase audit trail")
        assertThat(preference.summary.toString()).contains("Delete the local record")
    }

    @Test
    fun `a click asks before it erases`() {
        open()

        assertThat(dialog().isShowing).isTrue()
        val shown = texts(dialog().window!!.decorView)
        assertThat(shown).contains("Erase the audit trail?")
        assertThat(erased).isEqualTo(0)
    }

    @Test
    fun `the safe button has the focus when the dialog opens`() {
        open()

        val cancel = dialog().getButton(DialogInterface.BUTTON_NEGATIVE)
        val erase = dialog().getButton(DialogInterface.BUTTON_POSITIVE)
        assertThat(cancel.isFocusable).isTrue()
        assertThat(erase.isFocusable).isTrue()
        assertThat(cancel.hasFocus()).isTrue()
    }

    @Test
    fun `cancelling with the d-pad keeps the trail`() {
        open()

        press(KeyEvent.KEYCODE_DPAD_CENTER)

        assertThat(erased).isEqualTo(0)
        assertThat(dialog().isShowing).isFalse()
    }

    @Test
    fun `confirming with the d-pad erases and says so`() {
        open()
        val erase = dialog().getButton(DialogInterface.BUTTON_POSITIVE)
        erase.requestFocus()

        press(KeyEvent.KEYCODE_DPAD_CENTER)

        assertThat(erased).isEqualTo(1)
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Audit trail erased")
    }

    @Test
    fun `a failing erase is reported and does not crash`() {
        preference.eraser = { error("disk full") }
        open()

        dialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Could not erase the audit trail")
    }
}
