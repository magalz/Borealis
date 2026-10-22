package io.github.magalz.borealis.designsystem.testing

import android.content.res.Resources
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.ResourceFont

/**
 * The weight and the name of the resource of each file of this family, in the order the family
 * lists them: `400 to "b612_regular"`. The family of the device ([FontFamily.Default]) is not a
 * list of files, so asking it fails the test with a message that says so.
 */
fun FontFamily?.fontFiles(resources: Resources): List<Pair<Int, String>> {
    val fonts =
        checkNotNull((this as? FontListFontFamily)?.fonts) { "$this is not a list of files" }
    return fonts.map {
        it.weight.weight to resources.getResourceEntryName((it as ResourceFont).resId)
    }
}

/** The names of the resources of the files of this family, as [fontFiles] lists them. */
fun FontFamily?.fontFileNames(resources: Resources): List<String> =
    fontFiles(resources).map { it.second }
