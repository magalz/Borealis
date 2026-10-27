package io.github.magalz.borealis.stream

import androidx.activity.ComponentActivity
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The stream activity is a [ComponentActivity] that shows its start, conflict and failures through
 * the stream session instead of dialogs. The rig tests of `stream/game` characterize how it does
 * that; this reads the class without initializing it, so it needs no Android runtime.
 */
class GameHostingTest {
    @Test
    fun `Game is a ComponentActivity`() {
        val game = Class.forName("com.limelight.Game", false, javaClass.classLoader)

        assertThat(ComponentActivity::class.java.isAssignableFrom(game)).isTrue()
    }
}
