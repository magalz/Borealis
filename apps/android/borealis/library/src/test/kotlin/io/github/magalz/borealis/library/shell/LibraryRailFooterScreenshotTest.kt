package io.github.magalz.borealis.library.shell

import android.app.Application
import android.content.pm.PackageInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.testing.HANDHELD
import io.github.magalz.borealis.library.testing.TV
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Screenshots of the rail of the Library with the version of the app in its footer, named
 * `libraryrail_<device>_footer-version.png`. The package of these tests has no version name, so the
 * other screenshots of the rail show the name alone; here it reports [VERSION], a fixed string, so
 * the references do not depend on the build.
 */
@RunWith(AndroidJUnit4::class)
class LibraryRailFooterScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = TV)
    fun footerTvWithVersion() = shoot("libraryrail_tv_footer-version", FormFactor.Tv)

    @Test
    @Config(qualifiers = HANDHELD)
    fun footerHandheldWithVersion() =
        shoot("libraryrail_handheld_footer-version", FormFactor.Handheld)

    private fun shoot(name: String, formFactor: FormFactor) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.packageManager)
            .installPackage(
                PackageInfo().apply {
                    packageName = app.packageName
                    versionName = VERSION
                }
            )
        composeRule.setContent {
            LibraryScaffold(
                formFactor,
                selected = RailSelection.Hosts,
                actions = RailActions({}, {}, {}),
                backgroundStyle = BackgroundStyle.Off,
            ) {
                Box(Modifier.fillMaxSize())
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private companion object {
        const val VERSION = "0.1.0-test"
    }
}
