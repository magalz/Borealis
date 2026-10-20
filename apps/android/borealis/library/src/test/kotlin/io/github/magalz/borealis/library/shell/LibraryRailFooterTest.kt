package io.github.magalz.borealis.library.shell

import android.app.Application
import android.content.pm.PackageInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.testing.HANDHELD
import io.github.magalz.borealis.library.testing.PHONE
import io.github.magalz.borealis.library.testing.TV
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The footer of the Library rail: the name and the version of the app, read from the same place as
 * the About row of Settings.
 */
@RunWith(AndroidJUnit4::class)
class LibraryRailFooterTest {
    @get:Rule val composeRule = createComposeRule()

    private val app: Application
        get() = ApplicationProvider.getApplicationContext()

    private fun install(versionName: String?) {
        shadowOf(app.packageManager)
            .installPackage(
                PackageInfo().apply {
                    packageName = app.packageName
                    this.versionName = versionName
                }
            )
    }

    private fun show(formFactor: FormFactor) {
        composeRule.setContent {
            LibraryScaffold(
                formFactor,
                RailSelection.Hosts,
                RailActions({}, {}, {}),
                backgroundStyle = BackgroundStyle.Off,
            ) {
                Box(Modifier.fillMaxSize())
            }
        }
    }

    @Test
    @Config(qualifiers = TV)
    fun `the rail of a TV shows the name and the version of the app below its items`() {
        install("9.8.7")

        show(FormFactor.Tv)

        val footer = composeRule.onNodeWithContentDescription("Borealis, version 9.8.7")
        footer.assertIsDisplayed()
        composeRule.onNodeWithText("Borealis", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("9.8.7", useUnmergedTree = true).assertIsDisplayed()
        val settings = composeRule.onNodeWithText("Settings").getUnclippedBoundsInRoot()
        assertThat(footer.getUnclippedBoundsInRoot().top.value).isAtLeast(settings.bottom.value)
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `the rail of a handheld shows the name and the version too`() {
        install("9.8.7")

        show(FormFactor.Handheld)

        composeRule.onNodeWithContentDescription("Borealis, version 9.8.7").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TV)
    fun `a debug version name is shown as the package reports it`() {
        install("0.1.0-debug")

        show(FormFactor.Tv)

        composeRule
            .onNodeWithContentDescription("Borealis, version 0.1.0-debug")
            .assertIsDisplayed()
        composeRule.onNodeWithText("0.1.0-debug", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TV)
    fun `a version the system does not know leaves the name alone`() {
        install(null)

        show(FormFactor.Tv)

        composeRule.onNodeWithContentDescription("Borealis").assertIsDisplayed()
        composeRule.onNodeWithText("Borealis", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a phone has no rail and so no footer`() {
        install("9.8.7")

        show(FormFactor.Phone)

        composeRule.onNodeWithContentDescription("Borealis, version 9.8.7").assertDoesNotExist()
        composeRule.onNodeWithText("9.8.7", useUnmergedTree = true).assertDoesNotExist()
    }
}
