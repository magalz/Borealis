package io.github.magalz.borealis.settings.store

import android.content.SharedPreferences
import io.github.magalz.borealis.settings.catalog.Settings

/**
 * Moves the keys that older versions stored to the keys of the catalog, once each. The inherited
 * `PreferenceConfiguration` still reads the old bitrate key when the new one is absent.
 */
internal object LegacyMigrations {
    /** The bitrate in Mbps, from before the bitrate was stored in kbps. */
    private const val OLD_BITRATE_MBPS = "seekbar_bitrate"
    private const val KBPS_PER_MBPS = 1000

    /**
     * - A stored `checkbox_enable_perf_overlay=true` with no `borealis_perf_overlay` becomes
     *   Detailed, and the old key is removed whatever its value, so this happens once.
     * - A stored `seekbar_bitrate` (Mbps) with no `seekbar_bitrate_kbps` becomes that many kbps and
     *   the old key is removed. The inherited code reads the old key too, but the snapshot does
     *   not. A stored 0 stays as it is: it means automatic.
     */
    fun run(preferences: SharedPreferences) {
        val stored = preferences.all
        val editor = preferences.edit()
        var changed = false

        if (stored.containsKey(Settings.PERF_OVERLAY_LEGACY.key)) {
            if (
                !stored.containsKey(Settings.PERF_OVERLAY.key) &&
                    stored[Settings.PERF_OVERLAY_LEGACY.key] == true
            ) {
                editor.putString(Settings.PERF_OVERLAY.key, Settings.PERF_OVERLAY_DETAILED)
            }
            // Nothing reads the old switch any more. Left in place, it would bring Detailed back
            // on the next start after the user cleared the new key.
            editor.remove(Settings.PERF_OVERLAY_LEGACY.key)
            changed = true
        }

        val oldBitrate = stored[OLD_BITRATE_MBPS] as? Int
        if (
            !stored.containsKey(Settings.BITRATE_KBPS.key) && oldBitrate != null && oldBitrate > 0
        ) {
            editor.putInt(Settings.BITRATE_KBPS.key, oldBitrate * KBPS_PER_MBPS)
            editor.remove(OLD_BITRATE_MBPS)
            changed = true
        }

        if (changed) editor.apply()
    }
}
