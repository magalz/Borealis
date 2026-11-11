package io.github.magalz.borealis.stream

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * What the stream activity must not carry any more, and the resources that go with it. The activity
 * itself is characterized on the Robolectric rig of `stream/game`; what is left here reads no
 * source and no resource file, so it needs no Android runtime and never initializes `Game`. The
 * texts of the connection are pinned by the start tests of `connection`.
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

    // The names of the app's compiled resources of one type (`id`, `string`): a resource that comes
    // back in any layout or in the strings of any language is a field of its `R` class.
    private fun resourceNames(type: String): List<String> {
        val names =
            Class.forName("com.limelight.R\$$type", false, javaClass.classLoader)
                .declaredFields
                .map { it.name }
        // An empty class would let every check on these names pass without checking anything.
        assertThat(names).isNotEmpty()
        return names
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
        assertThat(resourceNames("id")).doesNotContain("performanceOverlay")
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
        assertThat(resourceNames("string").filter { it.startsWith("perf_overlay_") }).isEmpty()
    }

    @Test
    fun `the strings of the old error dialogs are gone in every language`() {
        val names =
            listOf("check_ports_msg", "conn_error_msg", "conn_terminated_msg", "error_code_prefix")

        assertThat(resourceNames("string").filter { it in names }).isEmpty()
    }
}
