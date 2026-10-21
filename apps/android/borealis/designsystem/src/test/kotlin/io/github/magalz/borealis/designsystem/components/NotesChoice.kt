package io.github.magalz.borealis.designsystem.components

import com.google.common.truth.Truth.assertWithMessage
import java.io.File

/**
 * The halo that `docs/ux/accessibility.md` records as the choice of the contrast tool, as the notes
 * print it: "The choice: stroke 3 dp, blur 2 dp, opacity 80 %." The tests take what the halo has to
 * look like from the notes, and not from the constants of the app, so that a change of the
 * constants that the notes do not follow fails.
 */
internal class NotesChoice
private constructor(val strokeDp: Int, val blurDp: Int, val opacity: Int) {
    companion object {
        private val CHOICE =
            Regex("""The choice: stroke (\d+) dp, blur (\d+) dp, opacity (\d+) %\.""")

        /** The notes are four levels above the module that a unit test runs in. */
        val NOTES = File("../../../../docs/ux/accessibility.md")

        fun read(): NotesChoice {
            assertWithMessage("the notes are not at ${NOTES.absolutePath}")
                .that(NOTES.isFile)
                .isTrue()
            val found = CHOICE.findAll(NOTES.readText()).toList()
            assertWithMessage("the notes have to say the choice once").that(found).hasSize(1)
            val (stroke, blur, opacity) = found.single().destructured
            return NotesChoice(stroke.toInt(), blur.toInt(), opacity.toInt())
        }
    }
}
