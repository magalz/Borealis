package io.github.magalz.borealis.stream

import android.app.Activity
import android.content.Intent
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class StreamRelaunchTest {
    private val application = shadowOf(RuntimeEnvironment.getApplication())
    private val intent = Intent("start.stream").putExtra("AppId", 42)

    @Test
    fun `starts the saved intent from the application after the activity is destroyed`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        StreamRelaunch.afterDestroy(controller.get(), intent)
        controller.get().finish()
        assertThat(application.nextStartedActivity).isNull()

        controller.pause().stop().destroy()

        val started = application.nextStartedActivity
        assertThat(started.action).isEqualTo("start.stream")
        assertThat(started.getIntExtra("AppId", 0)).isEqualTo(42)
        assertThat(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
    }

    @Test
    fun `starts it once, and not for another activity that is destroyed`() {
        val other = Robolectric.buildActivity(Activity::class.java).setup()
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        StreamRelaunch.afterDestroy(controller.get(), intent)
        other.get().finish()
        controller.get().finish()

        other.pause().stop().destroy()
        assertThat(application.nextStartedActivity).isNull()

        controller.pause().stop().destroy()
        assertThat(application.nextStartedActivity).isNotNull()
        assertThat(application.nextStartedActivity).isNull()
    }

    @Test
    fun `does not start it when the activity is only recreated`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        StreamRelaunch.afterDestroy(controller.get(), intent)

        // A destroy without finish() is a configuration change: the stream is still wanted.
        controller.pause().stop().destroy()

        assertThat(application.nextStartedActivity).isNull()
    }
}
