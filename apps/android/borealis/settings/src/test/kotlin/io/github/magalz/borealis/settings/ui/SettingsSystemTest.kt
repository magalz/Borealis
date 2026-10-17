package io.github.magalz.borealis.settings.ui

import android.app.Application
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** What the screen asks of the system: the device facts, the browser and the licence assets. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class SettingsSystemTest : SettingsScreenTestBase() {
    private val app: Application
        get() = ApplicationProvider.getApplicationContext()

    private fun realAbout(): AboutSource {
        lateinit var source: AboutSource
        composeRule.setContent { source = rememberAboutSource() }
        composeRule.waitForIdle()
        return source
    }

    @Test
    fun `the device facts are asked again when the configuration changes`() {
        var orientation by mutableIntStateOf(Configuration.ORIENTATION_PORTRAIT)
        val viewModel = viewModel()
        composeRule.setContent {
            val base = LocalConfiguration.current
            val configuration =
                remember(orientation) {
                    Configuration(base).apply { this.orientation = orientation }
                }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                SettingsRoute(
                    onBack = {},
                    onOpenPrivacy = {},
                    privacyPane = {},
                    about = testAbout(),
                    viewModel = viewModel,
                )
            }
        }
        composeRule.waitForIdle()
        val askedBefore = facts.calls

        orientation = Configuration.ORIENTATION_LANDSCAPE
        composeRule.waitForIdle()

        assertThat(facts.calls).isEqualTo(askedBefore + 1)
    }

    @Test
    fun `a link is opened in the app that takes it`() {
        val source = realAbout()

        val taken = source.openLink("https://github.com/magalz/Borealis")

        assertThat(taken).isTrue()
        val started = shadowOf(app).nextStartedActivity
        assertThat(started.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(started.data.toString()).isEqualTo("https://github.com/magalz/Borealis")
    }

    @Test
    fun `a link that no app takes is reported so that the address can be shown`() {
        shadowOf(app).checkActivities(true)
        val source = realAbout()

        val taken = source.openLink("https://github.com/magalz/Borealis")

        assertThat(taken).isFalse()
    }

    @Test
    fun `the version of the app comes from its package`() {
        val source = realAbout()

        assertThat(source.commit).isEqualTo("unknown")
    }
}
