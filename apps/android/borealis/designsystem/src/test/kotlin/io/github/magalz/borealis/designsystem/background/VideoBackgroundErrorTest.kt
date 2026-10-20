package io.github.magalz.borealis.designsystem.background

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.logging.LogTag
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A clip that cannot be played (a decoder that refuses it, one that is busy) must not leave a hole
 * or a black screen behind the Library: the still stays, the player is let go and the log says so
 * once.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class VideoBackgroundErrorTest {
    @get:Rule val composeRule = createComposeRule()

    private val players = FakeBackgroundPlayers()
    private val lifecycle = TestLifecycle()
    private val log = RecordingLogSink()

    @Before
    fun record() {
        BorealisLog.initialize(listOf(log), LogLevel.VERBOSE)
    }

    @After
    fun stopRecording() {
        BorealisLog.shutdown()
    }

    private fun show(style: BackgroundStyle) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalBackgroundPlayerFactory provides players,
                LocalLifecycleOwner provides lifecycle,
            ) {
                BorealisBackground(style, Modifier.fillMaxSize())
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `a player that fails is released and its still comes back over the surface`() {
        show(BackgroundStyle.Starfield)
        composeRule.runOnIdle { players.live.single().showFirstFrame() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BackgroundTags.STILL).assertDoesNotExist()

        composeRule.runOnIdle { players.live.single().fail(REASON) }
        composeRule.waitForIdle()

        assertThat(players.live).isEmpty()
        composeRule.onNodeWithTag(BackgroundTags.STILL).assertIsDisplayed()
    }

    @Test
    fun `a player that fails before its first frame leaves the still where it is`() {
        show(BackgroundStyle.LightMist)

        composeRule.runOnIdle { players.live.single().fail(REASON) }
        composeRule.waitForIdle()

        assertThat(players.live).isEmpty()
        composeRule.onNodeWithTag(BackgroundTags.STILL).assertIsDisplayed()
    }

    @Test
    fun `the failure is logged once, with the clip and the error and no address`() {
        show(BackgroundStyle.NorthernLights)

        composeRule.runOnIdle { players.live.single().fail(REASON) }
        composeRule.waitForIdle()

        val record = log.records.single()
        assertThat(record.level).isEqualTo(LogLevel.WARN)
        assertThat(record.tag).isEqualTo(LogTag.APP)
        assertThat(record.fields)
            .containsExactly("clip" to "background_northern_lights", "reason" to REASON)
        val text = record.message + record.fields.joinToString { it.second }
        assertThat(text).doesNotContain("/")
    }

    @Test
    fun `a clip that failed is not tried again when the screen comes back`() {
        show(BackgroundStyle.FlowingLight)
        composeRule.runOnIdle { players.live.single().fail(REASON) }
        composeRule.waitForIdle()

        lifecycle.moveTo(Lifecycle.State.CREATED)
        lifecycle.moveTo(Lifecycle.State.RESUMED)

        assertThat(players.created).hasSize(1)
        assertThat(log.records).hasSize(1)
        composeRule.onNodeWithTag(BackgroundTags.STILL).assertIsDisplayed()
    }

    private companion object {
        const val REASON = "ERROR_CODE_DECODER_INIT_FAILED"
    }
}
