package io.github.magalz.borealis.designsystem.components

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

/** Set on the node of a [BackdropIcon]: true where the halo is drawn behind its icon. */
val BackdropIconHalo = SemanticsPropertyKey<Boolean>("BackdropIconHalo")

var SemanticsPropertyReceiver.backdropIconHalo by BackdropIconHalo

/** An icon that stands straight on the animated background. A stub: it is a plain [Icon]. */
@Composable
fun BackdropIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(imageVector, contentDescription, modifier, tint)
}
