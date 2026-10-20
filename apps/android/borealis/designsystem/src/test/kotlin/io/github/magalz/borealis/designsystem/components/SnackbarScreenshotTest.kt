package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screenshots of the snackbar on a phone and on a TV, named like those of
 * [ComponentScreenshotTest]: a short message as wide as its text, and a long one held at 600dp on a
 * TV and wrapped on a phone.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalRoborazziApi::class)
class SnackbarScreenshotTest {
    @get:Rule val composeRule = createComposeRule()
    private val state = SnackbarHostState()

    @Test
    @Config(qualifiers = PHONE)
    fun snackbarPhone() {
        composeRule.setContent { Page(FormFactor.Phone, SHORT) }

        shoot("snackbar_phone")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun snackbarPhoneLong() {
        composeRule.setContent { Page(FormFactor.Phone, LONG) }

        shoot("snackbar_phone_long")
    }

    @Test
    @Config(qualifiers = TV)
    fun snackbarTv() {
        composeRule.setContent { Page(FormFactor.Tv, SHORT) }

        shoot("snackbar_tv")
    }

    @Test
    @Config(qualifiers = TV)
    fun snackbarTvLong() {
        composeRule.setContent { Page(FormFactor.Tv, LONG) }

        shoot("snackbar_tv_long")
    }

    @Composable
    private fun Page(formFactor: FormFactor, message: String) {
        BorealisTheme(formFactor) {
            LaunchedEffect(message) {
                state.showSnackbar(message, duration = SnackbarDuration.Indefinite)
            }
            Box(Modifier.fillMaxSize().background(LocalBorealisColors.current.page)) {
                BorealisSnackbarHost(
                    state,
                    Modifier.align(Alignment.BottomCenter).padding(16.dp),
                )
            }
        }
    }

    private fun shoot(name: String) {
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private companion object {
        const val PHONE = "w360dp-h640dp-xhdpi"
        const val TV = "w960dp-h540dp-land-television-xhdpi"
        const val SHORT = "App quit."
        const val LONG =
            "This session wasn't started by this device, so it cannot be quit. End streaming on " +
                "the original device or the PC itself. (Error code: 599)"
    }
}
