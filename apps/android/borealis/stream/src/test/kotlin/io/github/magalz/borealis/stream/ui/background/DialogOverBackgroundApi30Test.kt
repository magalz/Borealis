package io.github.magalz.borealis.stream.ui.background

import android.app.Application
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.testing.frame
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.testing.PHONE_LANDSCAPE
import io.github.magalz.borealis.stream.testing.RecordingIntents
import io.github.magalz.borealis.stream.testing.aFailure
import io.github.magalz.borealis.stream.testing.setChrome
import io.github.magalz.borealis.stream.ui.CONFLICT_PANEL_TAG
import io.github.magalz.borealis.stream.ui.ConflictDialog
import io.github.magalz.borealis.stream.ui.ConnectionErrorScreen
import io.github.magalz.borealis.stream.ui.ERROR_PANEL_TAG
import io.github.magalz.borealis.stream.ui.glass.leftPixel
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Below API 31 nothing blurs, so a dialog over the background of the screens outside the game keeps
 * its scrim and its solid panel (ADR 0057, decision 8: on API 28 to 30 the scrim stays).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.R], qualifiers = PHONE_LANDSCAPE)
class DialogOverBackgroundApi30Test {
    @get:Rule val composeRule = createComposeRule()

    private val surface = BorealisColors().surface
    private val page = BorealisColors().page
    private var open by mutableStateOf(false)
    private var dialog: @Composable () -> Unit = {
        ConflictDialog(
            StreamPhase.Conflict("Steam", "Desktop", sameApp = false),
            RecordingIntents(),
        )
    }

    private fun show() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        // The battery saver of the background listens with a receiver that older systems guard.
        shadowOf(app)
            .grantPermissions("${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        composeRule.setChrome(FormFactor.Phone) {
            OnStillBackground { if (open) dialog() }
        }
        composeRule.waitForIdle()
    }

    private fun errorPopup() {
        dialog = { ConnectionErrorScreen("Desktop", "Desk PC", aFailure, RecordingIntents()) }
    }

    private fun picture(): PixelMap = composeRule.frame(OUTSIDE_GAME_BACKGROUND_TAG)

    private fun Color.distanceFrom(other: Color) =
        abs(red - other.red) + abs(green - other.green) + abs(blue - other.blue)

    private fun openDialog() {
        composeRule.runOnIdle { open = true }
        composeRule.waitForIdle()
    }

    private fun assertScrim() {
        show()
        val before = picture()
        val (x, y) =
            (0 until before.width)
                .flatMap { x -> (0 until before.height / BAND).map { y -> x to y } }
                .maxBy { (x, y) -> before[x, y].distanceFrom(page) }

        openDialog()

        val shown = picture()[x, y].distanceFrom(page) / before[x, y].distanceFrom(page)
        assertWithMessage("how much of the background is left").that(shown).isLessThan(DIMMED)
    }

    private fun assertSolid(panelTag: String) {
        show()
        openDialog()

        val panel = composeRule.onNodeWithTag(panelTag).leftPixel()

        assertThat(panel.distanceFrom(surface)).isLessThan(SOLID)
    }

    @Test
    fun `the scrim dims the background where the question about a running app is over it`() =
        assertScrim()

    @Test
    fun `the scrim dims the background where the error popup is over it`() {
        errorPopup()

        assertScrim()
    }

    @Test
    fun `the panel of the question stays the solid surface`() = assertSolid(CONFLICT_PANEL_TAG)

    @Test
    fun `the panel of the error popup stays the solid surface`() {
        errorPopup()

        assertSolid(ERROR_PANEL_TAG)
    }

    private companion object {
        const val BAND = 10
        const val DIMMED = 0.45f
        const val SOLID = 0.005f
    }
}
