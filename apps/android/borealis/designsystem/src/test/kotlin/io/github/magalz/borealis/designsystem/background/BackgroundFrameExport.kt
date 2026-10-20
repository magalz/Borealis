package io.github.magalz.borealis.designsystem.background

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import java.util.Locale
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A tool, not a behaviour: it writes the frames of the two drawn styles that the contrast tool in
 * `design/accessibility/` measures (see `docs/ux/accessibility.md`).
 *
 * It runs only when the environment variable [OUT] names a folder, and it is skipped otherwise, so
 * a normal run writes nothing and records no reference. It writes [FRAMES] PNGs of each style at
 * evenly spaced points of its loop, `<out>/<style>-<device>/<NN>.png`, where `<style>` is the name
 * of the [BackgroundStyle] and `<device>` is `tv` or `phone`.
 *
 * ```
 * BOREALIS_FRAMES_OUT=<folder> ./gradlew :borealis:designsystem:testDebugUnitTest \
 *     --tests '*BackgroundFrameExport*' --rerun
 * ```
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalRoborazziApi::class)
class BackgroundFrameExport {
    @get:Rule val composeRule = createComposeRule()

    /** A TV at 1920 x 1080 pixels. */
    @Test @Config(qualifiers = TV) fun tv() = export("tv")

    /** A phone upright at 1233 x 2673 pixels. */
    @Test @Config(qualifiers = PHONE) fun phone() = export("phone")

    private fun export(device: String) {
        val out = System.getenv(OUT)
        assumeTrue("$OUT names no folder", !out.isNullOrBlank())

        val cycle = mutableFloatStateOf(0f)
        var style by mutableStateOf(BackgroundStyle.AuroraCurtains)
        composeRule.setContent {
            DrawnBackground(style, cycle, Modifier.fillMaxSize().testTag(BACKGROUND))
        }
        listOf(BackgroundStyle.AuroraCurtains, BackgroundStyle.SoftGlow).forEach { next ->
            composeRule.runOnIdle { style = next }
            repeat(FRAMES) { index ->
                composeRule.runOnIdle { cycle.floatValue = index.toFloat() / FRAMES }
                composeRule.waitForIdle()
                val name = String.format(Locale.ROOT, "%02d.png", index)
                val file = File(out, "${next.name}-$device/$name")
                composeRule.onNodeWithTag(BACKGROUND).captureRoboImage(file, WRITE)
            }
        }
    }

    private companion object {
        const val OUT = "BOREALIS_FRAMES_OUT"

        // Roborazzi captures only in a record, compare or verify run. This capture is a tool of its
        // own, so the task type makes it write the file whatever the run is.
        val WRITE = RoborazziOptions(taskType = RoborazziTaskType.Record)

        const val BACKGROUND = "background"
        const val FRAMES = 24
        const val TV = "w960dp-h540dp-xhdpi"
        const val PHONE = "w411dp-h891dp-xxhdpi"
    }
}
