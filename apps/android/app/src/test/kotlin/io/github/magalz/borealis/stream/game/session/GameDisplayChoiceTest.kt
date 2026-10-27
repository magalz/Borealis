package io.github.magalz.borealis.stream.game.session

import android.content.pm.PackageManager
import com.google.common.truth.Truth.assertThat
import com.limelight.preferences.PreferenceConfiguration
import io.github.magalz.borealis.stream.game.DisplayMode
import io.github.magalz.borealis.stream.game.GameTestBase
import io.github.magalz.borealis.stream.game.streamConfiguration
import org.junit.Test

/**
 * How `Game.prepareDisplayForRendering` picks the display mode for the stream, and what the pick
 * does to the stream that the activity asks the host for. The display is in the mode with the id 0;
 * the modes that a test adds come after it, in the order that the display lists them. A mode of
 * another resolution is asked for through the window (`preferredDisplayModeId`); a mode of the same
 * resolution is left to `Surface.setFrameRate`, so the refresh rate of the pick is what
 * `desiredRefreshRate` holds.
 */
internal class GameDisplayChoiceTest : GameTestBase() {
    /** What the stream asks for: its resolution, its frame rate and how the frames are paced. */
    private data class Wanted(
        val resolution: String = FULL_HD,
        val fps: Int = 60,
        val pacing: String = "latency",
        val reduce: Boolean = false,
    )

    /** What the pick came to: the mode asked of the window, its rate, and the rate reported. */
    private data class Picked(val modeId: Int, val refreshRate: Float, val reportedX100: Int)

    private fun pick(wanted: Wanted, current: DisplayMode, vararg others: DisplayMode): Picked {
        rig.preference("list_resolution", wanted.resolution)
        rig.preference("list_fps", wanted.fps.toString())
        rig.preference("frame_pacing", wanted.pacing)
        rig.preference("checkbox_reduce_refresh_rate", wanted.reduce)
        rig.displayModes(current, *others)

        rig.create()

        return Picked(
            rig.game.window.attributes.preferredDisplayModeId,
            rig.field("desiredRefreshRate"),
            rig.streamConfiguration.clientRefreshRateX100,
        )
    }

    private fun hd(id: Int, hz: Float) = DisplayMode(id, 1920, 1080, hz)

    // The current mode, and a mode that is the same one or no better

    @Test
    fun `a display that already is in the best mode stays in it`() {
        assertThat(pick(Wanted(), hd(0, 60f))).isEqualTo(Picked(0, 60f, 6000))
    }

    // The highest refresh rate, or the lowest

    @Test
    fun `minimum latency keeps the highest refresh rate that the frame rate divides`() {
        assertThat(pick(Wanted(), hd(0, 120f), hd(2, 60f))).isEqualTo(Picked(0, 120f, 12000))
    }

    @Test
    fun `balanced pacing keeps the highest refresh rate unless the user asks to reduce it`() {
        val picked = pick(Wanted(pacing = "balanced"), hd(0, 120f), hd(2, 60f))

        assertThat(picked).isEqualTo(Picked(0, 120f, 12000))
    }

    @Test
    fun `balanced pacing with the reduction on takes the lowest refresh rate that fits`() {
        val picked = pick(Wanted(pacing = "balanced", reduce = true), hd(0, 120f), hd(2, 60f))

        assertThat(picked).isEqualTo(Picked(0, 60f, 6000))
    }

    @Test
    fun `the setting that reduces the refresh rate counts for balanced pacing only`() {
        val picked = pick(Wanted(reduce = true), hd(0, 120f), hd(2, 60f))

        assertThat(picked).isEqualTo(Picked(0, 120f, 12000))
    }

    @Test
    fun `maximum smoothness takes the lowest refresh rate that fits`() {
        val picked = pick(Wanted(pacing = "smoothness"), hd(0, 120f), hd(2, 60f))

        assertThat(picked).isEqualTo(Picked(0, 60f, 6000))
    }

    @Test
    fun `capping the frame rate takes the lowest refresh rate that fits`() {
        val picked = pick(Wanted(pacing = "cap-fps"), hd(0, 120f), hd(2, 60f))

        assertThat(picked).isEqualTo(Picked(0, 60f, 6000))
    }

    @Test
    fun `a refresh rate that already equals the frame rate is kept when the rate may be reduced`() {
        val picked = pick(Wanted(pacing = "smoothness"), hd(0, 60f), hd(2, 120f))

        assertThat(picked).isEqualTo(Picked(0, 60f, 6000))
    }

    @Test
    fun `a refresh rate that equals the frame rate gives way to a higher one for minimum latency`() {
        val picked = pick(Wanted(), hd(0, 60f), hd(2, 120f))

        // The display shows 60 hertz for now, and that is what the stream reports.
        assertThat(picked).isEqualTo(Picked(0, 120f, 6000))
    }

    // What counts as a match for the frame rate

    @Test
    fun `a refresh rate three hertz above the frame rate is a match`() {
        assertThat(pick(Wanted(), hd(0, 60f), hd(2, 63f))).isEqualTo(Picked(0, 63f, 6000))
    }

    @Test
    fun `a refresh rate four hertz above the frame rate is no match`() {
        assertThat(pick(Wanted(), hd(0, 60f), hd(2, 64f))).isEqualTo(Picked(0, 60f, 6000))
    }

    @Test
    fun `a refresh rate that rounds half up past the margin is no match`() {
        assertThat(pick(Wanted(), hd(0, 60f), hd(2, 63.5f))).isEqualTo(Picked(0, 60f, 6000))
    }

    @Test
    fun `a refresh rate that rounds down within the margin is a match`() {
        assertThat(pick(Wanted(), hd(0, 60f), hd(2, 63.4f))).isEqualTo(Picked(0, 63.4f, 6000))
    }

    @Test
    fun `a multiple of the frame rate is a match`() {
        // 30 frames on 60 hertz: the display shows each frame twice.
        val picked = pick(Wanted(fps = 30), hd(0, 60f), hd(2, 90f))

        assertThat(picked).isEqualTo(Picked(0, 90f, 6000))
    }

    @Test
    fun `a refresh rate that is no multiple of the frame rate is no match`() {
        // 90 hertz is not a multiple of 60; 80 is no better: the display stays at the higher one.
        assertThat(pick(Wanted(), hd(0, 90f), hd(2, 80f))).isEqualTo(Picked(0, 90f, 9000))
    }

    @Test
    fun `without a match a higher refresh rate is taken`() {
        assertThat(pick(Wanted(), hd(0, 90f), hd(2, 100f))).isEqualTo(Picked(0, 100f, 9000))
    }

    @Test
    fun `a match is taken over a display that has none, even at a lower refresh rate`() {
        assertThat(pick(Wanted(), hd(0, 90f), hd(2, 60f))).isEqualTo(Picked(0, 60f, 6000))
    }

    @Test
    fun `a refresh rate just under the frame rate is no match, and a match above it wins`() {
        // 59.94 hertz is what a 60 frame stream gets from an NTSC display until a mode matches.
        val picked = pick(Wanted(), hd(0, 59.94f), hd(2, 60f))

        assertThat(picked).isEqualTo(Picked(0, 60f, 5994))
    }

    @Test
    fun `a refresh rate just past the equal margin is no equal match, a multiple takes its place`() {
        // 63.5 hertz is not within three hertz of 60, so the equal match that smoothness keeps is
        // not there, and the 120 hertz that 60 frames divide evenly is a better match than it.
        val picked = pick(Wanted(pacing = "smoothness"), hd(0, 63.5f), hd(2, 120f))

        assertThat(picked).isEqualTo(Picked(0, 120f, 6350))
    }

    // The resolution

    @Test
    fun `a stream at 60 frames takes no mode of another resolution`() {
        val picked = pick(Wanted(fps = 60), DisplayMode(0, 3840, 2160, 60f), hd(2, 60f))

        assertThat(picked).isEqualTo(Picked(0, 60f, 6000))
    }

    @Test
    fun `a stream above 60 frames may take a smaller resolution that fits it`() {
        val picked = pick(Wanted(fps = 61), DisplayMode(0, 3840, 2160, 60f), hd(2, 61f))

        // The display shows 60 hertz for now.
        assertThat(picked).isEqualTo(Picked(2, 61f, 6000))
    }

    @Test
    fun `a stream above 60 frames takes no resolution that is too small for it`() {
        val picked =
            pick(
                Wanted(fps = 120),
                DisplayMode(0, 3840, 2160, 60f),
                DisplayMode(2, 1280, 720, 120f),
            )

        assertThat(picked).isEqualTo(Picked(0, 60f, 6000))
    }

    @Test
    fun `a stream at 4K may take another resolution`() {
        val picked =
            pick(
                Wanted(resolution = "3840x2160"),
                DisplayMode(0, 3840, 2160, 60f),
                DisplayMode(2, 4096, 2160, 60f),
            )

        assertThat(picked).isEqualTo(Picked(2, 60f, 6000))
    }

    @Test
    fun `a stream under 4K keeps the resolution of the display`() {
        val picked =
            pick(
                Wanted(resolution = "2560x1440"),
                DisplayMode(0, 3840, 2160, 60f),
                DisplayMode(2, 2560, 1440, 60f),
            )

        assertThat(picked).isEqualTo(Picked(0, 60f, 6000))
    }

    @Test
    fun `a native resolution may take the mode of the display that has it`() {
        val picked =
            pick(Wanted(resolution = "2400x1080"), hd(0, 60f), DisplayMode(2, 2400, 1080, 60f))

        assertThat(picked).isEqualTo(Picked(2, 60f, 6000))
    }

    @Test
    fun `a standard resolution does not take a larger mode of the display`() {
        val picked = pick(Wanted(), hd(0, 60f), DisplayMode(2, 2400, 1080, 60f))

        assertThat(picked).isEqualTo(Picked(0, 60f, 6000))
    }

    @Test
    fun `a mode wider than 4K is left alone by a stream that is not`() {
        val picked = pick(Wanted(), hd(0, 60f), DisplayMode(2, 5120, 2880, 60f))

        assertThat(picked).isEqualTo(Picked(0, 60f, 6000))
    }

    @Test
    fun `a mode wider than 4K is taken by a stream that is as wide`() {
        val picked =
            pick(
                Wanted(resolution = "5120x2880"),
                hd(0, 60f),
                DisplayMode(2, 5120, 2880, 60f),
            )

        assertThat(picked).isEqualTo(Picked(2, 60f, 6000))
    }

    // What the pick does to the stream

    @Test
    fun `a phone reports the lower of the refresh rate that it shows and the one it picked`() {
        val picked = pick(Wanted(pacing = "smoothness"), hd(0, 120f), hd(2, 60f))

        assertThat(picked.reportedX100).isEqualTo(6000)
    }

    @Test
    fun `a television of the television feature reports the refresh rate that it will switch to`() {
        rig.feature(PackageManager.FEATURE_TELEVISION)

        val picked = pick(Wanted(fps = 120), DisplayMode(0, 3840, 2160, 60f), hd(2, 120f))

        assertThat(picked).isEqualTo(Picked(2, 120f, 12000))
    }

    // The frame rate that is capped

    private fun capped(fps: Int, hz: Float): Pair<Int, Int> {
        rig.preference("frame_pacing", "cap-fps")
        rig.preference("list_fps", fps.toString())
        rig.displayModes(DisplayMode(0, 1280, 720, hz))

        rig.create()

        return rig.streamConfiguration.refreshRate to
            rig.field<PreferenceConfiguration>("prefConfig").framePacing
    }

    @Test
    fun `a frame rate four above the refresh rate is no longer capped, frames are dropped`() {
        assertThat(capped(64, 60f)).isEqualTo(64 to PreferenceConfiguration.FRAME_PACING_BALANCED)
    }

    @Test
    fun `a frame rate under the refresh rate is not capped`() {
        assertThat(capped(59, 60f)).isEqualTo(59 to PreferenceConfiguration.FRAME_PACING_CAP_FPS)
    }

    @Test
    fun `a refresh rate of 50 hertz is capped to one frame less`() {
        assertThat(capped(50, 50f)).isEqualTo(49 to PreferenceConfiguration.FRAME_PACING_CAP_FPS)
    }

    @Test
    fun `a refresh rate of 49 hertz is bogus, and the frames are paced as balanced`() {
        assertThat(capped(50, 49f)).isEqualTo(50 to PreferenceConfiguration.FRAME_PACING_BALANCED)
    }

    @Test
    fun `a refresh rate is rounded half up before the frame rate is capped to it`() {
        // 59.94 hertz rounds to 60: a 60 frame stream is capped to 59, not 58.
        assertThat(capped(60, 59.94f)).isEqualTo(59 to PreferenceConfiguration.FRAME_PACING_CAP_FPS)
        assertThat(rig.streamConfiguration.clientRefreshRateX100).isEqualTo(5994)
    }

    @Test
    fun `a high refresh rate is capped to one frame less too`() {
        assertThat(capped(144, 144f)).isEqualTo(143 to PreferenceConfiguration.FRAME_PACING_CAP_FPS)
    }

    @Test
    fun `a frame rate that is not capped is the one that the stream launches with`() {
        capped(64, 60f)

        assertThat(rig.streamConfiguration.launchRefreshRate).isEqualTo(64)
    }

    private companion object {
        const val FULL_HD = "1920x1080"
    }
}
