package io.github.magalz.borealis.designsystem.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

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

/**
 * Whether the battery saver is on, now and while this is composed: it is read once and again each
 * time the system says it changed. A screen that animates for its looks rests while it is on.
 */
@Composable
internal fun rememberPowerSaveMode(): Boolean {
    val context = LocalContext.current
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    var saving by remember(power) { mutableStateOf(power.isPowerSaveMode) }
    DisposableEffect(context, power) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    saving = power.isPowerSaveMode
                }
            }
        // A system broadcast: only the system can send it, and nothing else is let in.
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // It may have changed between the first read and the registration.
        saving = power.isPowerSaveMode
        onDispose { context.unregisterReceiver(receiver) }
    }
    return saving
}
