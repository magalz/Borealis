package io.github.magalz.borealis.designsystem.adaptive

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class RememberFormFactorTest {
    @get:Rule val composeRule = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun `a touch phone is a phone`() {
        assertThat(detected()).isEqualTo(FormFactor.Phone)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun `the leanback feature makes a TV`() {
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)

        assertThat(detected()).isEqualTo(FormFactor.Tv)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun `the television UI mode makes a TV`() {
        val uiModeManager = context.getSystemService(UiModeManager::class.java)
        shadowOf(uiModeManager).setCurrentModeType(Configuration.UI_MODE_TYPE_TELEVISION)

        assertThat(detected()).isEqualTo(FormFactor.Tv)
    }

    @Test
    @Config(qualifiers = "w700dp-h400dp-xhdpi-dpad")
    fun `a wide device with a D-pad is a handheld`() {
        assertThat(detected()).isEqualTo(FormFactor.Handheld)
    }

    private fun detected(): FormFactor {
        var formFactor: FormFactor? = null
        composeRule.setContent { formFactor = rememberFormFactor() }
        composeRule.waitForIdle()
        return checkNotNull(formFactor) { "rememberFormFactor did not compose" }
    }
}
