package io.github.magalz.borealis.stream.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.host.failure.FailureKind
import io.github.magalz.borealis.host.failure.StreamFailure
import io.github.magalz.borealis.stream.testing.PHONE
import io.github.magalz.borealis.stream.testing.TV
import io.github.magalz.borealis.stream.testing.aSession
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screenshots of the connection error screen; the references live in `src/test/screenshots`, named
 * `<screen>_<device>_<state>.png`. The TV captures show Try again with the focus outline, as the
 * screen opens.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalRoborazziApi::class)
class ConnectionErrorScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = TV)
    fun errorTvBusyFocused() =
        shoot(
            "error_tv_busy_focused",
            FormFactor.Tv,
            StreamFailure(FailureKind.HOST_BUSY, "Error 503 · launch · HTTP 503", null),
        )

    @Test
    @Config(qualifiers = PHONE)
    fun errorPhoneOffline() =
        shoot(
            "error_phone_offline",
            FormFactor.Phone,
            StreamFailure(FailureKind.HOST_OFFLINE, "Error 0 · serverinfo", null),
            Target(hostName = "Living Room PC"),
        )

    @Test
    @Config(qualifiers = PHONE)
    fun errorPhoneCertificate() =
        shoot(
            "error_phone_certificate",
            FormFactor.Phone,
            StreamFailure(
                FailureKind.CERTIFICATE_CHANGED,
                "Error 401 · serverinfo · HTTP 401",
                null,
            ),
        )

    @Test
    @Config(qualifiers = TV)
    fun errorTvGenericPorts() =
        shoot(
            "error_tv_generic-ports",
            FormFactor.Tv,
            StreamFailure(
                FailureKind.GENERIC,
                "Error -1 · RTSP handshake",
                "Ports 47984, 47989, 48010 and 47998 to 48000 may be blocked",
            ),
            Target(appName = "Steam Big Picture"),
        )

    @Test
    @Config(qualifiers = TV)
    fun errorTvStoppedFocused() =
        shoot(
            "error_tv_stopped_focused",
            FormFactor.Tv,
            StreamFailure(FailureKind.STOPPED, "Error -1", null),
        )

    @Test
    @Config(qualifiers = PHONE)
    fun errorPhoneStopped() =
        shoot(
            "error_phone_stopped",
            FormFactor.Phone,
            StreamFailure(FailureKind.STOPPED, "Error -1", null),
        )

    @Test
    @Config(qualifiers = "w640dp-h300dp-land-xhdpi")
    fun errorPhoneLandscapeCertificateLargeText() =
        shoot(
            "error_phone-landscape_certificate-large-text",
            FormFactor.Phone,
            StreamFailure(
                FailureKind.CERTIFICATE_CHANGED,
                "Error 401 · serverinfo · HTTP 401",
                null,
            ),
            fontScale = 2f,
        )

    /** The app and the host that the failed connection was to. */
    private data class Target(val appName: String = "Desktop", val hostName: String = "Desk PC")

    private fun shoot(
        name: String,
        formFactor: FormFactor,
        failure: StreamFailure,
        target: Target = Target(),
        fontScale: Float = 1f,
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                BorealisTheme(formFactor) {
                    ConnectionErrorScreen(target.appName, target.hostName, failure, aSession())
                }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/$name.png")
    }
}
