package io.github.magalz.borealis.settings.audit

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.magalz.borealis.designsystem.testing.OnPage
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screenshots of the Audit trail in the states of the Phase 2 proposal; the references live in
 * `src/test/screenshots`, named `audit_<device>_<state>.png`. The entries are the ones of the
 * prototype, with example hosts.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalRoborazziApi::class)
class AuditScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

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
            AuditRow("LAUNCH_ATTEMPT", AuditType.LAUNCH_ATTEMPT, "20:33", "Desk PC · Desktop"),
            AuditRow("PAIRING_SUCCESS", AuditType.PAIRING_SUCCESS, "18:02", "Studio"),
            AuditRow("PAIRING_START", AuditType.PAIRING_START, "18:00", "Studio"),
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
    fun auditPhoneEmpty() = shoot("audit_phone_empty", FormFactor.Phone, AuditUiState(false))

    @Test
    @Config(qualifiers = PHONE)
    fun auditPhoneFilled() =
        shoot("audit_phone_filled", FormFactor.Phone, AuditUiState(false, rows))

    @Test
    @Config(qualifiers = PHONE)
    fun auditPhoneDetail() =
        shoot("audit_phone_detail", FormFactor.Phone, AuditUiState(false, rows, certificateDetail))

    @Test
    @Config(qualifiers = TV)
    fun auditTvEmpty() = shoot("audit_tv_empty", FormFactor.Tv, AuditUiState(false))

    @Test
    @Config(qualifiers = TV)
    fun auditTvFilled() = shoot("audit_tv_filled", FormFactor.Tv, AuditUiState(false, rows))

    @Test
    @Config(qualifiers = TV)
    fun auditTvDetail() =
        shoot("audit_tv_detail", FormFactor.Tv, AuditUiState(false, rows, certificateDetail))

    private fun shoot(name: String, formFactor: FormFactor, state: AuditUiState) {
        composeRule.setContent {
            OnPage {
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
