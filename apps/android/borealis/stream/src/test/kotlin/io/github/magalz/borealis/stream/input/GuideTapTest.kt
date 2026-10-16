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
        assertThat(GuideChord.isTap(early, hasSelect = true, hasMode = false)).isTrue()
    }

    @Test
    fun `releasing Start and Select early on a pad with a Mode button is no tap`() {
        assertThat(GuideChord.isTap(early, hasSelect = true, hasMode = true)).isFalse()
    }

    @Test
    fun `a chord that opened the menu is no tap`() {
        assertThat(GuideChord.isTap(fired, hasSelect = true, hasMode = false)).isFalse()
    }

    @Test
    fun `an event that did not end the chord is no tap`() {
        assertThat(GuideChord.isTap(nothing, hasSelect = true, hasMode = false)).isFalse()
    }
}
