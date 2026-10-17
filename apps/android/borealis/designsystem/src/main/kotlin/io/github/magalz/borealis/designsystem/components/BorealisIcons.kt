package io.github.magalz.borealis.designsystem.components

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** The Borealis icon set; see the implementation commit. */
object BorealisIcons {
    private fun empty(name: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).build()

    val Back = empty("Back")
    val ChevronRight = empty("ChevronRight")
    val ChevronDown = empty("ChevronDown")
    val Check = empty("Check")
    val Gear = empty("Gear")
    val Hosts = empty("Hosts")
    val Add = empty("Add")
    val Visibility = empty("Visibility")
    val VisibilityOff = empty("VisibilityOff")
}
