package io.github.magalz.borealis.support

import android.app.Activity
import android.app.AlertDialog
import android.os.Looper
import android.view.View
import com.google.common.truth.Truth.assertThat
import com.limelight.utils.Dialog
import java.lang.reflect.Modifier
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlertDialog

/** The dialog with one OK button that the stream and the decoder crash notice use. */
@RunWith(RobolectricTestRunner::class)
class DialogCharacterizationTest {
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).create().get()

    private fun latest(): AlertDialog? = ShadowAlertDialog.getLatestAlertDialog()

    @Test
    fun `a dialog shows its title and its message`() {
        Dialog.displayDialog(activity, "A title", "A message", Runnable {})

        val dialog = checkNotNull(latest())
        assertThat(dialog.isShowing).isTrue()
        assertThat(shadowOf(dialog).title.toString()).isEqualTo("A title")
        assertThat(shadowOf(dialog).message.toString()).isEqualTo("A message")
    }

    @Test
    fun `a dialog has an OK button and nothing else`() {
        Dialog.displayDialog(activity, "A title", "A message", Runnable {})

        val dialog = checkNotNull(latest())
        assertThat(dialog.getButton(AlertDialog.BUTTON_POSITIVE).text.toString())
            .isEqualTo(activity.resources.getText(android.R.string.ok).toString())
        assertThat(dialog.getButton(AlertDialog.BUTTON_POSITIVE).visibility).isEqualTo(View.VISIBLE)
        assertThat(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).visibility).isEqualTo(View.GONE)
        assertThat(dialog.getButton(AlertDialog.BUTTON_NEUTRAL).visibility).isEqualTo(View.GONE)
    }

    @Test
    fun `a dialog cannot be cancelled by back or by a touch outside`() {
        Dialog.displayDialog(activity, "A title", "A message", Runnable {})

        val dialog = checkNotNull(latest())
        assertThat(shadowOf(dialog).isCancelable).isFalse()
        assertThat(shadowOf(dialog).isCancelableOnTouchOutside).isFalse()
    }

    @Test
    fun `the OK button can take the focus, also in touch mode`() {
        Dialog.displayDialog(activity, "A title", "A message", Runnable {})
        // The dialog tells its show listener through its handler.
        shadowOf(Looper.getMainLooper()).idle()

        val button = checkNotNull(latest()).getButton(AlertDialog.BUTTON_POSITIVE)
        assertThat(button.isFocusable).isTrue()
        assertThat(button.isFocusableInTouchMode).isTrue()
    }

    @Test
    fun `OK dismisses the dialog and then runs what the caller wanted done`() {
        var dismissedWhenRun: Boolean? = null
        var runs = 0
        Dialog.displayDialog(
            activity,
            "A title",
            "A message",
            Runnable {
                runs++
                dismissedWhenRun = !checkNotNull(latest()).isShowing
            },
        )
        assertThat(runs).isEqualTo(0)

        checkNotNull(latest()).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(runs).isEqualTo(1)
        assertThat(dismissedWhenRun).isTrue()
        assertThat(checkNotNull(latest()).isShowing).isFalse()
    }

    @Test
    fun `a dialog for an activity that is finishing is not made`() {
        var runs = 0
        activity.finish()

        Dialog.displayDialog(activity, "A title", "A message", Runnable { runs++ })

        assertThat(latest()).isNull()
        assertThat(runs).isEqualTo(0)
    }

    @Test
    fun `a dialog asked for on another thread is shown on the main thread`() {
        val thread = Thread {
            Dialog.displayDialog(activity, "A title", "From a thread", Runnable {})
        }

        thread.start()
        thread.join()

        assertThat(latest()).isNull()
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(shadowOf(checkNotNull(latest())).message.toString()).isEqualTo("From a thread")
    }

    @Test
    fun `each call makes a dialog of its own`() {
        Dialog.displayDialog(activity, "First", "one", Runnable {})
        val first = checkNotNull(latest())

        Dialog.displayDialog(activity, "Second", "two", Runnable {})
        val second = checkNotNull(latest())

        assertThat(second).isNotSameInstanceAs(first)
        assertThat(shadowOf(first).title.toString()).isEqualTo("First")
        assertThat(shadowOf(second).title.toString()).isEqualTo("Second")
    }

    @Test
    fun `a dialog is a runnable that can only be made by displayDialog`() {
        assertThat(Runnable::class.java.isAssignableFrom(Dialog::class.java)).isTrue()
        // Kotlin adds a synthetic constructor for the companion; Java source cannot call it.
        val declared = Dialog::class.java.declaredConstructors.filterNot { it.isSynthetic }
        assertThat(declared.map { Modifier.isPrivate(it.modifiers) }).containsExactly(true)
    }
}
