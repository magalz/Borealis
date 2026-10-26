package io.github.magalz.borealis.library.shell

import android.app.Application
import android.content.pm.PackageInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.noAnimations
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import io.github.magalz.borealis.library.testing.HANDHELD
import io.github.magalz.borealis.library.testing.PHONE
import io.github.magalz.borealis.library.testing.TV
import kotlin.math.abs
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

    private fun show(formFactor: FormFactor, style: BackgroundStyle = BackgroundStyle.Off) {
        composeRule.setContent { Frame(formFactor, style) }
    }

    @Composable
    private fun Frame(formFactor: FormFactor, style: BackgroundStyle) {
        LibraryScaffold(
            formFactor,
            RailSelection.Hosts,
            RailActions({}, {}, {}),
            backgroundStyle = style,
        ) {
            Box(Modifier.fillMaxSize())
        }
    }

    /** The glass rail: the Standard look over the still frame of the curtains. */
    private fun showOnGlass(keyboard: Boolean = false) {
        noAnimations()
        if (keyboard) {
            composeRule.setContentInKeyboardMode { Frame(FormFactor.Tv, GLASS_STYLE) }
        } else {
            show(FormFactor.Tv, GLASS_STYLE)
        }
        composeRule.waitForIdle()
        assertThat(railDistanceFromChrome()).isGreaterThan(GLASS_NOT_CHROME)
    }

    /**
     * How far the rail, level with the middle of the window and between its items and its footer,
     * is from the chrome colour that it has where the glass is solid: the sum of the channels.
     */
    private fun railDistanceFromChrome(): Float {
        val shot = composeRule.onRoot().captureToImage().toPixelMap()
        val rail = shot[RAIL_PROBE_PX, shot.height / 2]
        val chrome = BorealisTokens.Color.Chrome
        return abs(rail.red - chrome.red) +
            abs(rail.green - chrome.green) +
            abs(rail.blue - chrome.blue)
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
    @Config(qualifiers = TV)
    fun `on the glass rail the footer is read as one sentence and skips the mark`() {
        install("9.8.7")

        showOnGlass()

        composeRule.onNodeWithContentDescription("Borealis, version 9.8.7").assertIsDisplayed()
        // The name, the version and the mark are not nodes of their own to a screen reader.
        composeRule.onNodeWithText("Borealis").assertDoesNotExist()
        composeRule.onNodeWithText("9.8.7").assertDoesNotExist()
        composeRule
            .onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription),
                useUnmergedTree = true,
            )
            .assertCountEquals(1)
    }

    @Test
    @Config(qualifiers = TV)
    fun `on the glass rail the footer takes no focus and Down from the last item does not reach it`() {
        install("9.8.7")
        showOnGlass(keyboard = true)
        val footer = composeRule.onNodeWithContentDescription("Borealis, version 9.8.7")

        // Not a focus target: it has no focus state, and no request for focus to answer.
        footer.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused))
        footer.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.RequestFocus))
        composeRule.onNodeWithText("Settings").requestFocus()
        composeRule.onNodeWithText("Settings").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()

        composeRule.onAllNodes(isFocused()).assertCountEquals(1)
        composeRule.onNode(isFocused()).assert(hasText("Settings"))
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a phone has no rail and so no footer`() {
        install("9.8.7")

        show(FormFactor.Phone)

        composeRule.onNodeWithContentDescription("Borealis, version 9.8.7").assertDoesNotExist()
        composeRule.onNodeWithText("9.8.7", useUnmergedTree = true).assertDoesNotExist()
    }

    private companion object {
        val GLASS_STYLE = BackgroundStyle.AuroraCurtains

        // 20 pixels is 10dp at xhdpi: in the padding of the rail, left of the items.
        const val RAIL_PROBE_PX = 20

        // The glass rail is about 0.1 from the chrome colour, the sum of red, green and blue.
        const val GLASS_NOT_CHROME = 0.05f
    }
}
