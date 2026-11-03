package io.github.magalz.borealis.settings

import android.content.Context
import android.os.VibrationAttributes
import android.os.Vibrator
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AndroidVibrationSampleTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val vibrator = shadowOf(context.getSystemService(Vibrator::class.java))
    private val sample = AndroidVibrationSample(context)

    @Test
    fun `a device with a vibrator gets one pulse of the duration asked`() {
        vibrator.setHasVibrator(true)

        sample.play(60, 100)

        assertThat(vibrator.isVibrating).isTrue()
        assertThat(vibrator.milliseconds).isEqualTo(60)
    }

    @Test
    fun `the pulse has the usage of the stream vibration`() {
        vibrator.setHasVibrator(true)

        sample.play(60, 100)

        val attributes = vibrator.vibrationAttributesFromLastVibration as VibrationAttributes?
        assertThat(attributes?.usage).isEqualTo(VibrationAttributes.USAGE_MEDIA)
    }

    @Test
    fun `a device without amplitude control still gets the pulse`() {
        vibrator.setHasVibrator(true)
        vibrator.setHasAmplitudeControl(false)

        sample.play(60, 150)

        assertThat(vibrator.isVibrating).isTrue()
    }

    @Test
    fun `a device without a vibrator plays nothing`() {
        vibrator.setHasVibrator(false)

        sample.play(60, 100)

        assertThat(vibrator.isVibrating).isFalse()
    }

    @Test
    fun `a strength of zero plays nothing`() {
        vibrator.setHasVibrator(true)

        sample.play(60, 0)

        assertThat(vibrator.isVibrating).isFalse()
    }

    @Test
    fun `a strength out of the range of the setting is kept in it and never throws`() {
        vibrator.setHasVibrator(true)

        sample.play(60, -20)
        assertThat(vibrator.isVibrating).isFalse()

        sample.play(60, 1_000)
        assertThat(vibrator.isVibrating).isTrue()
    }

    @Test
    fun `the amplitude follows the strength from 1 to 255`() {
        assertThat(sampleAmplitude(0)).isEqualTo(0)
        assertThat(sampleAmplitude(1)).isEqualTo(1)
        assertThat(sampleAmplitude(100)).isEqualTo(127)
        assertThat(sampleAmplitude(200)).isEqualTo(255)
    }

    @Test
    fun `the amplitude stays in the range of the vibrator out of the range of the setting`() {
        assertThat(sampleAmplitude(-50)).isEqualTo(0)
        assertThat(sampleAmplitude(1_000)).isEqualTo(255)
    }
}
