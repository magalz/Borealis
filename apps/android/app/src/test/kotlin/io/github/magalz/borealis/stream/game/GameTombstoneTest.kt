package io.github.magalz.borealis.stream.game

import android.content.Context
import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import com.limelight.binding.video.CrashListener
import org.junit.Test
import org.robolectric.util.ReflectionHelpers

/**
 * The decoder crash tombstone: the `DecoderTombstone` shared preferences, whose `CrashCount` the
 * crash listener raises and `onStop` resets, and whose `LastNotifiedCrashCount` the crash dialog of
 * the app reads. The keys and the file name are a persistent format.
 */
internal class GameTombstoneTest : GameTestBase() {
    private val tombstone: SharedPreferences
        get() = rig.application.getSharedPreferences("DecoderTombstone", Context.MODE_PRIVATE)

    /** A tombstone as an earlier run of the app left it. */
    private fun leftBehind(crashCount: Int?, lastNotified: Int?) {
        val editor = tombstone.edit()
        crashCount?.let { editor.putInt("CrashCount", it) }
        lastNotified?.let { editor.putInt("LastNotifiedCrashCount", it) }
        editor.commit()
    }

    private fun crashListener(): CrashListener =
        ReflectionHelpers.getField(rig.renderer, "crashListener")

    @Test
    fun `the renderer is told how many crashes the earlier runs left`() {
        leftBehind(crashCount = 2, lastNotified = 1)

        rig.create()

        assertThat(ReflectionHelpers.getField<Int>(rig.renderer, "consecutiveCrashCount"))
            .isEqualTo(2)
    }

    @Test
    fun `the renderer starts with no crash when there is no tombstone`() {
        rig.create()

        assertThat(ReflectionHelpers.getField<Int>(rig.renderer, "consecutiveCrashCount"))
            .isEqualTo(0)
    }

    @Test
    fun `a crash of the decoder raises the crash count by one and is remembered`() {
        leftBehind(crashCount = 2, lastNotified = 2)
        rig.create()

        crashListener().notifyCrash(IllegalStateException("decoder"))

        assertThat(tombstone.getInt("CrashCount", -1)).isEqualTo(3)
        assertThat(tombstone.getInt("LastNotifiedCrashCount", -1)).isEqualTo(2)
        assertThat(rig.field<Boolean>("reportedCrash")).isTrue()
    }

    @Test
    fun `the first crash writes a crash count of one`() {
        rig.create()

        crashListener().notifyCrash(IllegalStateException("decoder"))

        assertThat(tombstone.getInt("CrashCount", -1)).isEqualTo(1)
    }

    @Test
    fun `a stream that ended without a crash clears both counts`() {
        leftBehind(crashCount = 2, lastNotified = 2)
        rig.create()

        rig.stop()

        assertThat(tombstone.getInt("CrashCount", -1)).isEqualTo(0)
        assertThat(tombstone.getInt("LastNotifiedCrashCount", -1)).isEqualTo(0)
    }

    @Test
    fun `a stream that crashed keeps its counts`() {
        leftBehind(crashCount = 2, lastNotified = 2)
        rig.create()
        crashListener().notifyCrash(IllegalStateException("decoder"))

        rig.stop()

        assertThat(tombstone.getInt("CrashCount", -1)).isEqualTo(3)
        assertThat(tombstone.getInt("LastNotifiedCrashCount", -1)).isEqualTo(2)
    }

    @Test
    fun `a stream that ended without a crash leaves an empty tombstone as it was`() {
        leftBehind(crashCount = 0, lastNotified = 5)
        rig.create()

        rig.stop()

        assertThat(tombstone.getInt("CrashCount", -1)).isEqualTo(0)
        assertThat(tombstone.getInt("LastNotifiedCrashCount", -1)).isEqualTo(5)
    }

    @Test
    fun `a run without a tombstone writes none`() {
        rig.create()

        rig.stop()

        assertThat(tombstone.contains("CrashCount")).isFalse()
        assertThat(tombstone.contains("LastNotifiedCrashCount")).isFalse()
    }
}
