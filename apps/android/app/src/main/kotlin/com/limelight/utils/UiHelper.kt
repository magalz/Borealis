package com.limelight.utils

import android.app.Activity
import android.app.GameManager
import android.app.GameState
import android.content.Context
import android.os.Build
import androidx.core.content.edit
import com.limelight.R
import com.limelight.preferences.PreferenceConfiguration

object UiHelper {
    private const val DECODER_TOMBSTONE_PREFS = "DecoderTombstone"
    private const val CRASH_COUNT = "CrashCount"
    private const val LAST_NOTIFIED_CRASH_COUNT = "LastNotifiedCrashCount"

    // The settings are reset at every third crash in a row.
    private const val CRASHES_BEFORE_RESET = 3

    // Swallow any failure. Some OEM builds ship partial/incompatible GameManager impls.
    @Suppress("TooGenericExceptionCaught")
    private fun setGameModeStatus(context: Context, streaming: Boolean, interruptible: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                // Not supported on this device (e.g., Meta Quest)
                val gameManager = context.getSystemService(GameManager::class.java) ?: return

                if (streaming) {
                    gameManager.setGameState(
                        GameState(
                            false,
                            if (interruptible) {
                                GameState.MODE_GAMEPLAY_INTERRUPTIBLE
                            } else {
                                GameState.MODE_GAMEPLAY_UNINTERRUPTIBLE
                            },
                        )
                    )
                } else {
                    gameManager.setGameState(GameState(false, GameState.MODE_NONE))
                }
            } catch (ignored: Throwable) {
                // Swallowed: see above.
            }
        }
    }

    @JvmStatic fun notifyStreamConnecting(context: Context) = setGameModeStatus(context, true, true)

    @JvmStatic fun notifyStreamConnected(context: Context) = setGameModeStatus(context, true, false)

    @JvmStatic
    fun notifyStreamEnteringPiP(context: Context) = setGameModeStatus(context, true, true)

    @JvmStatic
    fun notifyStreamExitingPiP(context: Context) = setGameModeStatus(context, true, false)

    @JvmStatic fun notifyStreamEnded(context: Context) = setGameModeStatus(context, false, false)

    @JvmStatic
    fun showDecoderCrashDialog(activity: Activity) {
        val prefs = activity.getSharedPreferences(DECODER_TOMBSTONE_PREFS, 0)
        val crashCount = prefs.getInt(CRASH_COUNT, 0)
        val lastNotifiedCrashCount = prefs.getInt(LAST_NOTIFIED_CRASH_COUNT, 0)

        // Remember the last crash count we notified at, so we don't
        // display the crash dialog every time the app is started until
        // they stream again
        if (crashCount != 0 && crashCount != lastNotifiedCrashCount) {
            // Mark notification as acknowledged on dismissal
            val acknowledge = Runnable {
                prefs.edit { putInt(LAST_NOTIFIED_CRASH_COUNT, crashCount) }
            }
            if (crashCount % CRASHES_BEFORE_RESET == 0) {
                // At 3 consecutive crashes, we'll forcefully reset their settings
                PreferenceConfiguration.resetStreamingSettings(activity)
                Dialog.displayDialog(
                    activity,
                    activity.resources.getString(R.string.title_decoding_reset),
                    activity.resources.getString(R.string.message_decoding_reset),
                    acknowledge,
                )
            } else {
                Dialog.displayDialog(
                    activity,
                    activity.resources.getString(R.string.title_decoding_error),
                    activity.resources.getString(R.string.message_decoding_error),
                    acknowledge,
                )
            }
        }
    }
}
