package io.github.magalz.borealis.designsystem.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the system removes animations: the setting "Remove animations" sets the animator scale to
 * zero. An animated piece then shows its end state at once. It is read once per composition.
 */
@Composable
internal fun rememberAnimationsRemoved(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
