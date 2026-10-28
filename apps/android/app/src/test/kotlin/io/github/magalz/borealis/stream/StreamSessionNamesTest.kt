package io.github.magalz.borealis.stream

import android.app.Activity
import com.google.common.truth.Truth.assertThat
import com.limelight.preferences.PreferenceConfiguration
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * What the session starts with for the app of the intent, which can be missing or a placeholder.
 */
@RunWith(RobolectricTestRunner::class)
class StreamSessionNamesTest {
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).get()

    private fun appNameOf(appName: String?) =
        StreamSessionFactory.create(
                activity,
                PreferenceConfiguration(),
                appName,
                "Den PC",
                RecordingStreamActions(),
            ) {
                false
            }
            .state
            .value
            .appName

    @Test
    fun `the name of the intent is the name of the session`() {
        assertThat(appNameOf("Desktop")).isEqualTo("Desktop")
    }

    @Test
    fun `a missing name stays empty`() {
        assertThat(appNameOf(null)).isEmpty()
    }

    @Test
    fun `the placeholder that upstream gives a resumed app is not a name`() {
        assertThat(appNameOf("app")).isEmpty()
    }
}
