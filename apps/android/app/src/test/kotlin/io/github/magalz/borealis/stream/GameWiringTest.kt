package io.github.magalz.borealis.stream

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * What the stream activity must not carry any more, and the texts of the connection and the
 * resources that go with it. The activity itself is characterized on the Robolectric rig of
 * `stream/game`; what is left here reads no source of the activity, so it needs no Android runtime
 * and never initializes `Game`.
 */
class GameWiringTest {
    // The names of the renderer's own fields and methods, read without initializing the class.
    private fun rendererMembers(): List<String> {
        val renderer =
            Class.forName(
                "com.limelight.binding.video.MediaCodecDecoderRenderer",
                false,
                javaClass.classLoader,
            )
        return renderer.declaredFields.map { it.name } + renderer.declaredMethods.map { it.name }
    }

    // The names of the activity's own fields, methods, nested classes and interfaces, read without
    // initializing the class.
    private fun gameMembers(): List<String> {
        val game = Class.forName("com.limelight.Game", false, javaClass.classLoader)
        return game.declaredFields.map { it.name } +
            game.declaredMethods.map { it.name } +
            game.declaredClasses.map { it.name } +
            game.interfaces.map { it.name }
    }

    @Test
    fun `the connection never hands an exception text to the listener`() {
        val connection =
            File("src/main/kotlin/com/limelight/nvstream/NvConnection.kt").readText()

        assertThat(connection).doesNotContain("displayMessage(e.message")
        assertThat(connection).doesNotContain("stageFailed(appName")
    }

    @Test
    fun `sending the launch or the resume is the step that fails after the serverinfo`() {
        val connection =
            File("src/main/kotlin/com/limelight/nvstream/NvConnection.kt").readText()
        val start = connection.indexOf("private fun launchWithRetry(")
        val launch = connection.substring(start, connection.indexOf("\n    }\n", start))

        // The launch needs the native library, so a unit test cannot run it: read the wiring.
        assertThat(launch).contains("startStep = verb")
        assertThat(connection).contains("startStep = StartSteps.SERVER_INFO")
        assertThat(connection).contains("stageFailed(startStep")
    }

    @Test
    fun `a failed quit is the quit step, set before the quit request is sent`() {
        val connection =
            File("src/main/kotlin/com/limelight/nvstream/NvConnection.kt").readText()
        val start = connection.indexOf("protected open fun quitAndLaunch(")
        val quit = connection.substring(start, connection.indexOf("\n    }\n", start))

        assertThat(quit).contains("startStep = StartSteps.QUIT")
        assertThat(quit.indexOf("startStep = StartSteps.QUIT"))
            .isLessThan(quit.indexOf("h.quitApp()"))
    }

    @Test
    fun `the 12 point 2 text overlay is gone`() {
        val leftovers = listOf("performanceOverlay", "onPerfUpdate", "PerfOverlayListener")
        assertThat(
                gameMembers().filter { member ->
                    leftovers.any { member.contains(it, ignoreCase = true) }
                }
            )
            .isEmpty()
        assertThat(rendererMembers().filter { it.contains("perfListener", ignoreCase = true) })
            .isEmpty()
        // No code can name a perf_overlay_ string: no strings.xml holds one (the next test).
        assertThat(File("src/main/res/layout/activity_game.xml").readText())
            .doesNotContain("performanceOverlay")
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName(
                "com.limelight.binding.video.PerfOverlayListener",
                false,
                javaClass.classLoader,
            )
        }
    }

    @Test
    fun `no string of the text overlay stays in any language`() {
        val leftovers =
            File("src/main/res")
                .walkTopDown()
                .filter { it.name == "strings.xml" && it.readText().contains("perf_overlay_") }
                .map { it.path }
                .toList()

        assertThat(leftovers).isEmpty()
    }

    @Test
    fun `the strings of the old error dialogs are gone in every language`() {
        val names = listOf("check_ports_msg", "conn_error_msg", "conn_terminated_msg", "error_code_prefix")
        val leftovers =
            File("src/main/res")
                .walkTopDown()
                .filter { file ->
                    file.name == "strings.xml" && names.any { file.readText().contains("\"$it\"") }
                }
                .map { it.path }
                .toList()

        assertThat(leftovers).isEmpty()
    }
}
