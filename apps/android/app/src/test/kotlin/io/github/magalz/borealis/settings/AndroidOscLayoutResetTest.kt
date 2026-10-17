package io.github.magalz.borealis.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidOscLayoutResetTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun prefs(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    @Test
    fun `reset clears the saved layout`() {
        prefs("OSC").edit().putString("BUTTON_A", "layout").putInt("OPACITY", 3).commit()

        AndroidOscLayoutReset(context).reset()

        assertThat(prefs("OSC").all).isEmpty()
    }

    @Test
    fun `reset leaves every other preferences file alone`() {
        prefs("OSC").edit().putString("BUTTON_A", "layout").commit()
        prefs("GlPreferences").edit().putString("Renderer", "Adreno").commit()
        prefs("other").edit().putBoolean("kept", true).commit()

        AndroidOscLayoutReset(context).reset()

        assertThat(prefs("GlPreferences").getString("Renderer", null)).isEqualTo("Adreno")
        assertThat(prefs("other").getBoolean("kept", false)).isTrue()
    }

    @Test
    fun `reset with no saved layout does nothing`() {
        AndroidOscLayoutReset(context).reset()

        assertThat(prefs("OSC").all).isEmpty()
    }
}
