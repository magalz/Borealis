package io.github.magalz.borealis.library.pairing

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screenshots of the Pairing screen in the states of the Phase 2 proposal; the references live in
 * `src/test/screenshots`, named `pairing_<device>_<state>.png`. The sample data is made up: the Web
 * UI address is a TEST-NET address.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalRoborazziApi::class)
class PairingScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = PHONE)
    fun pairingPhoneWaiting() = shoot("pairing_phone_waiting", FormFactor.Phone, waiting())

    @Test
    @Config(qualifiers = PHONE)
    fun pairingPhoneNewPin() =
        shoot("pairing_phone_new-pin", FormFactor.Phone, waiting(pin = "2093", restarted = true))

    @Test
    @Config(qualifiers = PHONE)
    fun pairingPhonePaired() =
        shoot("pairing_phone_paired", FormFactor.Phone, PairingUiState.Paired)

    @Test
    @Config(qualifiers = PHONE)
    fun pairingPhoneExpired() =
        shoot("pairing_phone_expired", FormFactor.Phone, PairingUiState.Expired)

    @Test
    @Config(qualifiers = PHONE)
    fun pairingPhoneFailed() =
        shoot(
            "pairing_phone_failed",
            FormFactor.Phone,
            PairingUiState.Failed(PairingFailure.PIN_WRONG, null),
        )

    @Test
    @Config(qualifiers = TV)
    fun pairingTvWaiting() = shoot("pairing_tv_waiting", FormFactor.Tv, waiting())

    @Test
    @Config(qualifiers = TV)
    fun pairingTvExpired() = shoot("pairing_tv_expired", FormFactor.Tv, PairingUiState.Expired)

    private fun waiting(pin: String = "4817", restarted: Boolean = false) =
        PairingUiState.Waiting(pin, "https://192.0.2.10:47990", "Galaxy S22", restarted)

    private fun shoot(name: String, formFactor: FormFactor, state: PairingUiState) {
        composeRule.setContent {
            PairingScreen(
                state = state,
                formFactor = formFactor,
                onGetNewPin = {},
                onCancel = {},
                hostName = "Studio",
            )
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private companion object {
        const val PHONE = "w360dp-h640dp-xhdpi"
        const val TV = "w960dp-h540dp-land-television-xhdpi"
    }
}
