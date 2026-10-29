package io.github.magalz.borealis.stream.ui.glass

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.testing.RecordingIntents
import io.github.magalz.borealis.stream.testing.aPerf
import io.github.magalz.borealis.stream.ui.StreamingLayer
import io.github.magalz.borealis.stream.ui.aStreaming
import org.robolectric.Shadows.shadowOf

/** Where the pixels of the panel are read: inside its edge, where no row or button is drawn. */
private const val EDGE_PX = 8

/** The Quit popup is announced by its question, which is its pane title. */
internal const val QUIT_QUESTION = "Quit Desktop?"

/** The consequence under the Refresh row, which is in the secondary ink. */
internal const val REFRESH_NOTE = "Asks the host for a keyframe when the image is smeared"

/** A lifecycle that a test moves by hand, resumed to begin with. */
internal class TestLifecycle : LifecycleOwner {
    private val registry =
        LifecycleRegistry(this).also { it.currentState = Lifecycle.State.RESUMED }

    override val lifecycle: Lifecycle
        get() = registry

    fun moveTo(state: Lifecycle.State) {
        registry.currentState = state
    }
}

/**
 * The streaming layer over a stream whose video is [source] (null: no stream view), the way the
 * chrome draws it, with the look, the phase and the glass option that a test changes while it is on
 * the screen. The clock of the test is its own: [run] moves it, and the copies tick with it.
 */
internal class GlassFixture(
    private val rule: ComposeContentTestRule,
    val source: FakeVideoSource? = FakeVideoSource(completing = true),
) {
    var look by mutableStateOf(BorealisLook())
    var phase by mutableStateOf(aStreaming(Panel.MENU))
    var videoGlass by mutableStateOf(true)
    var formFactor = FormFactor.Phone
    var keyboardMode = false

    /** What the stream shows behind the chrome, for a screenshot: the picture of the fake video. */
    var behind: @Composable () -> Unit = {}
    val lifecycle = TestLifecycle()
    val intents = RecordingIntents()

    fun show() {
        val content: @Composable () -> Unit = {
            BorealisTheme(formFactor, look) {
                CompositionLocalProvider(
                    LocalVideoSource provides source,
                    LocalLifecycleOwner provides lifecycle,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        behind()
                        StreamingLayer(
                            "Desktop",
                            "Gaming PC",
                            phase,
                            aPerf(),
                            intents,
                            videoGlass = videoGlass,
                        )
                    }
                }
            }
        }
        if (keyboardMode) rule.setContentInKeyboardMode(content) else rule.setContent(content)
        run(FIRST_FRAME_MS)
    }

    /** Lets [millis] of the test clock pass, in frames, and what ticks on it ticks. */
    fun run(millis: Long) {
        rule.mainClock.advanceTimeBy(millis)
        rule.waitForIdle()
    }

    /** The menu asks for Quit, and the popup is up. */
    fun openQuitPopup() {
        rule.onNodeWithText("Quit Desktop").performScrollTo().performClick()
        run(FIRST_FRAME_MS)
    }

    /** The menu asks for Quit with the keys, so the popup opens with the focus on Cancel. */
    fun openQuitFromKeys() {
        rule.onNodeWithText("Quit Desktop").performScrollTo().requestFocus()
        rule.onNodeWithText("Quit Desktop").performKeyInput { pressKey(Key.DirectionCenter) }
        run(FIRST_FRAME_MS)
    }

    /** The side panel of the menu, which is the node that scrolls. */
    fun panel(): SemanticsNodeInteraction = rule.onNode(hasScrollAction())

    /** The Quit popup, by its question. */
    fun popup(): SemanticsNodeInteraction = rule.onNode(popupMatcher())

    fun popupMatcher(): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, QUIT_QUESTION)

    private companion object {
        const val FIRST_FRAME_MS = 16L
    }
}

/** The colour just inside the left edge of this node, at half its height. */
internal fun SemanticsNodeInteraction.leftPixel(): Color =
    captureToImage().toPixelMap().let { it[EDGE_PX, it.height / 2] }

/** The colour just inside the right edge of this node, at half its height. */
internal fun SemanticsNodeInteraction.rightPixel(): Color =
    captureToImage().toPixelMap().let { it[it.width - EDGE_PX, it.height / 2] }

/** Turns the battery saver on or off, and tells the app the way the system does. */
internal fun setPowerSave(on: Boolean) {
    val context = ApplicationProvider.getApplicationContext<Context>()
    shadowOf(context.getSystemService(PowerManager::class.java)).setIsPowerSaveMode(on)
    context.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
    shadowOf(Looper.getMainLooper()).idle()
}
