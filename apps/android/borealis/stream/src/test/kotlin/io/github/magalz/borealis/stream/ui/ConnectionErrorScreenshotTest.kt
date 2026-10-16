package io.github.magalz.borealis.stream.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.host.failure.FailureKind
import io.github.magalz.borealis.host.failure.StreamFailure
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
            "Desktop",
            "Desk PC",
            StreamFailure(FailureKind.HOST_BUSY, "Error 0 · /serverinfo timed out 3 times", null),
        )

    @Test
    @Config(qualifiers = PHONE)
    fun errorPhoneOffline() =
        shoot(
            "error_phone_offline",
            FormFactor.Phone,
            "Desktop",
            "Living Room PC",
            StreamFailure(FailureKind.HOST_OFFLINE, "Error 0 · no answer on port 47989", null),
        )

    @Test
    @Config(qualifiers = PHONE)
    fun errorPhoneCertificate() =
        shoot(
            "error_phone_certificate",
            FormFactor.Phone,
            "Desktop",
            "Desk PC",
            StreamFailure(FailureKind.CERTIFICATE_CHANGED, "CERTIFICATE_CHANGED", null),
        )

    @Test
    @Config(qualifiers = TV)
    fun errorTvGenericPorts() =
        shoot(
            "error_tv_generic-ports",
            FormFactor.Tv,
            "Steam Big Picture",
            "Desk PC",
            StreamFailure(
                FailureKind.GENERIC,
                "Error -1 · RTSP handshake",
                "Ports 47984, 47989, 48010 and 47998 to 48000 may be blocked",
            ),
        )

    private fun shoot(
        name: String,
        formFactor: FormFactor,
        appName: String,
        hostName: String,
        failure: StreamFailure,
    ) {
        composeRule.setContent {
            BorealisTheme(formFactor) {
                ConnectionErrorScreen(appName, hostName, failure, aSession())
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/$name.png")
    }

    private companion object {
        const val PHONE = "w360dp-h640dp-xhdpi"
        const val TV = "w960dp-h540dp-land-television-xhdpi"
    }
}
