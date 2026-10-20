package io.github.magalz.borealis.stream

import android.app.Activity
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.ColourVision
import io.github.magalz.borealis.designsystem.theme.ReduceMotion
import io.github.magalz.borealis.designsystem.theme.TextSize
import io.github.magalz.borealis.designsystem.theme.Transparency
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.store.SettingsSnapshots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * The look that the stream chrome starts with is the one that the settings store when the stream
 * starts, mapped as the Library maps it. The stream reads it once.
 */
@RunWith(RobolectricTestRunner::class)
class StreamSessionFactoryLookTest {
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).create().get()

    @Test
    fun `nothing stored gives the default look`() {
        assertThat(StreamSessionFactory.look(activity)).isEqualTo(BorealisLook())
    }

    @Test
    fun `the stored choices are the look of the stream`() {
        val settings = SettingsSnapshots.repository(activity)
        settings.setNow(Settings.REDUCE_MOTION, "on")
        settings.setNow(Settings.TRANSPARENCY, "reduced")
        settings.setNow(Settings.TEXT_SIZE, "larger")
        settings.setNow(Settings.COLOUR_VISION, "red_green")
        settings.setNow(Settings.THICK_FOCUS, true)
        settings.setNow(Settings.HIGH_CONTRAST, true)

        assertThat(StreamSessionFactory.look(activity))
            .isEqualTo(
                BorealisLook(
                    transparency = Transparency.Reduced,
                    reduceMotion = ReduceMotion.On,
                    thickFocus = true,
                    textSize = TextSize.Larger,
                    highContrast = true,
                    colourVision = ColourVision.RedGreen,
                )
            )
    }

    @Test
    fun `a stored id that nothing has gives the default`() {
        SettingsSnapshots.repository(activity).setNow(Settings.TEXT_SIZE, "huge")

        assertThat(StreamSessionFactory.look(activity).textSize).isEqualTo(TextSize.Default)
    }
}
