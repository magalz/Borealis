package io.github.magalz.borealis.stream.input

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Which pads send Start and Select as a Guide tap, and when. */
class GuideTapTest {
    private val early = MenuChordDetector.Update(fired = false, earlyRelease = true, silent = true)
    private val fired = MenuChordDetector.Update(fired = true, earlyRelease = false, silent = false)
    private val nothing =
        MenuChordDetector.Update(fired = false, earlyRelease = false, silent = false)

    @Test
    fun `a pad with Select and no Mode button maps Start and Select to Guide`() {
        assertThat(GuideChord.mapsStartSelect(hasSelect = true, hasMode = false)).isTrue()
    }

    @Test
    fun `a pad with a Mode button sends its own Guide button`() {
        assertThat(GuideChord.mapsStartSelect(hasSelect = true, hasMode = true)).isFalse()
    }

    @Test
    fun `a pad without Select has no Start and Select combination`() {
        assertThat(GuideChord.mapsStartSelect(hasSelect = false, hasMode = false)).isFalse()
    }

    @Test
    fun `releasing Start and Select early on a mapped pad is a Guide tap`() {
        assertThat(GuideChord.tapButtons(early, hasSelect = true, hasMode = false))
            .isEqualTo(GamepadButtons.GUIDE)
    }

    @Test
    fun `releasing Start and Select early on a pad with a Mode button is a short press of both`() {
        assertThat(GuideChord.tapButtons(early, hasSelect = true, hasMode = true))
            .isEqualTo(GamepadButtons.START or GamepadButtons.SELECT)
    }

    @Test
    fun `releasing Start and Select early on a pad without that information is a short press of both`() {
        assertThat(GuideChord.tapButtons(early, hasSelect = false, hasMode = false))
            .isEqualTo(GamepadButtons.START or GamepadButtons.SELECT)
    }

    @Test
    fun `a chord that opened the menu sends no tap`() {
        assertThat(GuideChord.tapButtons(fired, hasSelect = true, hasMode = false)).isEqualTo(0)
        assertThat(GuideChord.tapButtons(fired, hasSelect = true, hasMode = true)).isEqualTo(0)
    }

    @Test
    fun `an event that did not end the chord sends no tap`() {
        assertThat(GuideChord.tapButtons(nothing, hasSelect = true, hasMode = false)).isEqualTo(0)
    }

    @Test
    fun `a lone button let go while it was held back is a short press of that button`() {
        val start = MenuChordDetector.Update(false, false, true, loneTap = GamepadButtons.START)
        val select = MenuChordDetector.Update(false, false, true, loneTap = GamepadButtons.SELECT)

        assertThat(GuideChord.tapButtons(start, hasSelect = true, hasMode = false))
            .isEqualTo(GamepadButtons.START)
        assertThat(GuideChord.tapButtons(select, hasSelect = true, hasMode = true))
            .isEqualTo(GamepadButtons.SELECT)
    }
}
