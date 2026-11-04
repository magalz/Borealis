package io.github.magalz.borealis.settings.ui

import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.settings.catalog.Settings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The screen plays a sample pulse when the user turns a vibration on or lets go of its slider. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class SettingsVibrationSampleTest : PhoneSettingsTestBase() {
    private val pulses = mutableListOf<Pair<Long, Int>>()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun viewModel(): SettingsViewModel =
        SettingsViewModel(
            repository,
            options,
            facts,
            { resets++ },
            { durationMs, strength -> pulses += durationMs to strength },
            UnconfinedTestDispatcher(),
        )

    private fun title(item: SettingsItem): String =
        RuntimeEnvironment.getApplication().getString(item.titleRes)

    @Test
    fun `letting go of the Vibration strength slider plays a pulse at the value that was set`() {
        showOpen()

        setProgress(title(SettingsItem.VIBRATION_STRENGTH), 150f)

        assertThat(snapshot[Settings.VIBRATE_FALLBACK_STRENGTH]).isEqualTo(150)
        assertThat(pulses).containsExactly(60L to 150)
    }

    @Test
    fun `turning on the vibration fallback plays a pulse at the strength that is stored`() {
        stored(Settings.VIBRATE_FALLBACK_STRENGTH to 80)
        showOpen()

        row(title(SettingsItem.VIBRATE_FALLBACK)).performClick()
        composeRule.waitForIdle()

        assertThat(snapshot[Settings.VIBRATE_FALLBACK]).isTrue()
        assertThat(pulses).containsExactly(60L to 80)
    }
}
