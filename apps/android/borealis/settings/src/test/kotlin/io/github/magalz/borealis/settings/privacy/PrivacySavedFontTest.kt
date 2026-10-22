package io.github.magalz.borealis.settings.privacy

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.fontFileNames
import io.github.magalz.borealis.designsystem.testing.textLayout
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FontChoice
import io.github.magalz.borealis.designsystem.theme.FormFactor
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Saved dialog of the diagnostics bundle draws the `adb pull` command as a code, in the
 * monospaced companion of the font of the look (ADR 0058, decision 2), not in the monospace of the
 * system; the location is text in the font itself.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class PrivacySavedFontTest {
    @get:Rule val composeRule = createComposeRule()

    private val saved =
        PrivacyDialog.Saved(
            location = "/sdcard/Download/$BUNDLE",
            adbPullCommand = ADB_COMMAND,
            bundle = File("/sdcard/Download/$BUNDLE"),
            fileName = BUNDLE,
            sizeBytes = SAMPLE_SIZE.toLong(),
        )

    private fun show(font: FontChoice) {
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv, BorealisLook(font = font)) {
                PrivacyDialogs(saved, PrivacyDialogActions({}, {}, {}, { _: File, _: String -> }))
            }
        }
        composeRule.waitForIdle()
    }

    private fun files(text: String): List<String> =
        composeRule
            .onNodeWithText(text, useUnmergedTree = true)
            .textLayout()
            .layoutInput
            .style
            .fontFamily
            .fontFileNames(ApplicationProvider.getApplicationContext<Context>().resources)

    @Test
    fun `with B612 the adb command draws in B612 Mono`() {
        show(FontChoice.B612)

        assertThat(files(ADB_COMMAND))
            .containsExactly("b612_mono_regular", "b612_mono_bold")
            .inOrder()
    }

    @Test
    fun `with the default font the adb command draws in IBM Plex Mono`() {
        show(FontChoice.Plex)

        assertThat(files(ADB_COMMAND))
            .containsExactly(
                "ibm_plex_mono_regular",
                "ibm_plex_mono_medium",
                "ibm_plex_mono_semibold",
            )
            .inOrder()
    }

    @Test
    fun `with B612 the location draws in B612, as text`() {
        show(FontChoice.B612)

        assertThat(files("/sdcard/Download/$BUNDLE"))
            .containsExactly("b612_regular", "b612_bold")
            .inOrder()
    }

    private companion object {
        const val BUNDLE = "borealis-diagnostics-20261016-224700.zip"
        const val ADB_COMMAND = "adb pull /sdcard/Download/$BUNDLE"
    }
}
