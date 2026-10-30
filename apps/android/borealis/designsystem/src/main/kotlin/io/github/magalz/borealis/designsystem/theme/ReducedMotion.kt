package io.github.magalz.borealis.designsystem.theme

import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Whether animations are removed: the Reduce motion setting of the app decides, and Follow system
 * hands it to the system. An animated piece then shows its end state at once. It is read again
 * while this is composed, when the setting or the system animation scale changes.
 *
 * Every animation of the app asks this, so that Reduce motion reaches all of them. A piece that has
 * no composition to ask in (a modifier node) reads [LocalBorealisLook] and the system itself, and
 * decides with [animationsRemoved]. Under a [BorealisTheme] the system animation scale is watched
 * once for all of them.
 */
@Composable
fun rememberAnimationsRemoved(): Boolean =
    animationsRemoved(LocalBorealisLook.current.reduceMotion, rememberSystemAnimationsRemoved())

/**
 * Whether the system removes animations, as the [BorealisTheme] around the reader found it: the
 * theme watches the setting once and every piece under it reads that. Null outside a theme, where a
 * reader watches the setting itself.
 */
internal val LocalSystemAnimationsRemoved = compositionLocalOf<Boolean?> { null }

/**
 * Whether the system removes animations: what the theme around this has found, or, outside a theme,
 * what this watches for itself.
 */
@Composable
internal fun rememberSystemAnimationsRemoved(): Boolean =
    LocalSystemAnimationsRemoved.current ?: watchSystemAnimationsRemoved()

/**
 * Whether the system removes animations: the setting "Remove animations" sets the animator scale to
 * zero. It is read once and again each time the system says that the scale changed, through an
 * observer that lives as long as this is composed.
 */
@Composable
private fun watchSystemAnimationsRemoved(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var removed by remember(resolver) { mutableStateOf(systemAnimationsRemoved(resolver)) }
    DisposableEffect(resolver) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    removed = systemAnimationsRemoved(resolver)
                }
            }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        // It may have changed between the first read and the registration.
        removed = systemAnimationsRemoved(resolver)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return removed
}

/** Whether the system animation scale is zero now: the setting "Remove animations". */
internal fun systemAnimationsRemoved(resolver: ContentResolver): Boolean =
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** Whether animations are removed, given the setting of the app and what the system says. */
internal fun animationsRemoved(reduceMotion: ReduceMotion, systemRemoved: Boolean): Boolean =
    when (reduceMotion) {
        ReduceMotion.FollowSystem -> systemRemoved
        ReduceMotion.On -> true
        ReduceMotion.Off -> false
    }

/**
 * Whether the battery saver is on, now and while this is composed: it is read once and again each
 * time the system says it changed. A screen that animates for its looks rests while it is on.
 */
@Composable
fun rememberPowerSaveMode(): Boolean {
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
