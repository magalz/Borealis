package io.github.magalz.borealis.stream

import androidx.activity.ComponentActivity
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * The stream activity is a [ComponentActivity] that shows its start, conflict and failures through
 * the stream session instead of dialogs. Reads the source, so it needs no Android runtime and never
 * initializes `Game`.
 */
class GameHostingTest {
    private val source: String = File("src/main/java/com/limelight/Game.java").readText()

    private fun method(name: String): String {
        val start = source.indexOf("public void $name(")
        check(start >= 0) { "$name not found" }
        // The next method of the NvConnectionListener block ends this one.
        val end = source.indexOf("\n    @Override", start)
        return source.substring(start, end)
    }

    @Test
    fun `Game is a ComponentActivity`() {
        val game = Class.forName("com.limelight.Game", false, javaClass.classLoader)

        assertThat(ComponentActivity::class.java.isAssignableFrom(game)).isTrue()
    }

    @Test
    fun `Game uses neither the spinner nor the conflict dialog`() {
        assertThat(source).doesNotContain("SpinnerDialog")
        assertThat(source).doesNotContain("LaunchConflictDialog")
    }

    @Test
    fun `a failed stage and a terminated connection reach the session, not a dialog`() {
        for (name in listOf("stageFailed", "connectionTerminated")) {
            val body = method(name)
            assertThat(body).doesNotContain("Dialog.displayDialog")
            assertThat(body).contains("streamSession.onFailed(")
        }
    }

    @Test
    fun `the input entry points ask the chrome first`() {
        for (name in listOf("handleKeyDown", "handleKeyUp", "handleKeyMultiple", "handleMotionEvent")) {
            val start = Regex("(public|private) boolean $name\(").find(source)!!.range.first
            val body = source.substring(start, source.indexOf("{", start) + 200)
            assertThat(body).contains("chromeInput.toChrome(")
        }
    }
}
