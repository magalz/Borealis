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
            val start = Regex("(public|private) boolean $name\\(").find(source)!!.range.first
            val body = source.substring(start, source.indexOf("{", start) + 200)
            assertThat(body).contains("chromeInput.toChrome(")
        }
    }

    private fun body(signature: String): String {
        val start = source.indexOf(signature)
        check(start >= 0) { "$signature not found" }
        return source.substring(start, source.indexOf("\n    }\n", start))
    }

    @Test
    fun `taking the input forgets the modifier state, so no key goes on with a stale Shift`() {
        val body = body("private void chromeCaptured()")

        assertThat(body).contains("modifierFlags = 0")
        assertThat(body).contains("specialKeyCode = KeyEvent.KEYCODE_UNKNOWN")
        assertThat(body).contains("waitingForAllModifiersUp = false")
    }

    @Test
    fun `giving the input back grabs only when the user had it grabbed before`() {
        assertThat(body("private void chromeCaptured()"))
            .contains("grabbedBeforeCapture = grabbedInput")
        assertThat(body("private void chromeReleased()")).contains("grabbedBeforeCapture")
    }

    @Test
    fun `the hardware Escape key acts as Back while the chrome is modal`() {
        assertThat(body("public boolean onKeyDown(")).contains("chromeInput.actsAsBack(")
        assertThat(body("public boolean onKeyUp(")).contains("chromeInput.actsAsBack(")
    }
}
