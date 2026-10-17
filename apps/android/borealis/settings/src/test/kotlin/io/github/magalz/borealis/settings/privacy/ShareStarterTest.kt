package io.github.magalz.borealis.settings.privacy

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/** The share sheet is started from an activity when there is one, and from a new task otherwise. */
@RunWith(AndroidJUnit4::class)
class ShareStarterTest {
    private val application: Context = ApplicationProvider.getApplicationContext()

    private fun chooser() = Intent.createChooser(Intent(Intent.ACTION_SEND), "Share diagnostics")

    @Test
    fun `an activity starts the share sheet as it is`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        activity.startShareSheet(chooser())

        val started = shadowOf(activity).nextStartedActivity
        assertThat(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isEqualTo(0)
    }

    @Test
    fun `an activity behind wrappers starts the share sheet as it is`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        ContextWrapper(ContextWrapper(activity)).startShareSheet(chooser())

        val started = shadowOf(activity).nextStartedActivity
        assertThat(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isEqualTo(0)
    }

    @Test
    fun `a context with no activity starts the share sheet in a new task`() {
        ContextWrapper(application).startShareSheet(chooser())

        val started = shadowOf(application as android.app.Application).nextStartedActivity
        assertThat(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
            .isEqualTo(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
