package io.github.magalz.borealis.settings.audit

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.noAnimations
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import io.github.magalz.borealis.settings.testing.InGlassHost
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screenshots of the Audit trail on glass, over the still of Northern lights, named
 * `auditglass_<device>_<state>.png`.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalRoborazziApi::class)
class AuditGlassScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    @Before fun theBackgroundIsItsStill() = noAnimations()

    private val rows =
        listOf(
            AuditRow(
                "SESSION_END",
                AuditType.SESSION_END,
                "20:42",
                "Desk PC · Desktop · 8 min 12 s",
            ),
            AuditRow(
                "SESSION_START",
                AuditType.SESSION_START,
                "20:34",
                "Desk PC · HEVC Main10 · 2560×1440 · 59 fps",
            ),
            AuditRow("PAIRING_SUCCESS", AuditType.PAIRING_SUCCESS, "18:02", "Studio"),
            AuditRow(
                "CERTIFICATE_CHANGED",
                AuditType.CERTIFICATE_CHANGED,
                "Mon 17:00",
                "Living Room PC · pair again if you did not reinstall the host",
            ),
        )

    private val certificateDetail =
        AuditDetail(
            AuditType.CERTIFICATE_CHANGED,
            "12 Oct 2026 17:00:41",
            AuditOutcome.FAILURE,
            "Living Room PC",
            "pair again if you did not reinstall the host",
        )

    @Test
    @Config(qualifiers = PHONE)
    fun auditPhoneFilled() =
        shoot("auditglass_phone_filled", FormFactor.Phone, AuditUiState(false, rows))

    @Test
    @Config(qualifiers = PHONE)
    fun auditPhoneDetail() =
        shoot(
            "auditglass_phone_detail",
            FormFactor.Phone,
            AuditUiState(false, rows, certificateDetail),
        )

    @Test
    @Config(qualifiers = TV)
    fun auditTvFilled() = shoot("auditglass_tv_filled", FormFactor.Tv, AuditUiState(false, rows))

    private fun shoot(name: String, formFactor: FormFactor, state: AuditUiState) {
        composeRule.setContent {
            InGlassHost(formFactor, BorealisLook(), BackgroundStyle.NorthernLights) {
                AuditScreen(
                    state = state,
                    formFactor = formFactor,
                    onEntrySelected = {},
                    onDetailDismissed = {},
                    onBack = {},
                )
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/$name.png")
    }
}
