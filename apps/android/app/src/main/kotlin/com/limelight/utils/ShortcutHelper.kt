package com.limelight.utils

import android.content.Context
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import com.limelight.R
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp

// Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
@Suppress("TooManyFunctions")
class ShortcutHelper(private val context: Context) {
    private val sm: ShortcutManager = context.getSystemService(ShortcutManager::class.java)
    private val tvChannelHelper = TvChannelHelper(context)

    private fun reapShortcutsForDynamicAdd() {
        var dynamicShortcuts = sm.dynamicShortcuts
        while (
            dynamicShortcuts.isNotEmpty() && dynamicShortcuts.size >= sm.maxShortcutCountPerActivity
        ) {
            var maxRankShortcut = dynamicShortcuts[0]
            for (scut in dynamicShortcuts) {
                if (maxRankShortcut.rank < scut.rank) {
                    maxRankShortcut = scut
                }
            }
            sm.removeDynamicShortcuts(listOf(maxRankShortcut.id))

            // Read the list again, so the next pass sees what the removal left. A launcher that
            // refuses the removal leaves it as it was: stop, or this would never end.
            val remaining = sm.dynamicShortcuts
            if (remaining.size >= dynamicShortcuts.size) return
            dynamicShortcuts = remaining
        }
    }

    private fun getAllShortcuts(): List<ShortcutInfo> = sm.dynamicShortcuts + sm.pinnedShortcuts

    private fun getInfoForId(id: String?): ShortcutInfo? =
        getAllShortcuts().firstOrNull { it.id == id }

    private fun isExistingDynamicShortcut(id: String?): Boolean =
        sm.dynamicShortcuts.any { it.id == id }

    fun reportComputerShortcutUsed(computer: ComputerDetails) {
        if (getInfoForId(computer.uuid) != null) {
            sm.reportShortcutUsed(computer.uuid)
        }
    }

    fun reportGameLaunched(computer: ComputerDetails, app: NvApp) {
        tvChannelHelper.createTvChannel(computer)
        tvChannelHelper.addGameToChannel(computer, app)
    }

    fun createHostShortcut(computer: ComputerDetails, forceAdd: Boolean, newlyPaired: Boolean) {
        val sinfo =
            ShortcutInfo.Builder(context, computer.uuid)
                .setIntent(ServerHelper.createPcShortcutIntent(context, computer))
                .setShortLabel(checkNotNull(computer.name))
                .setLongLabel(checkNotNull(computer.name))
                .setIcon(Icon.createWithResource(context, R.mipmap.ic_pc_scut))
                .build()

        val existingSinfo = getInfoForId(computer.uuid)
        if (existingSinfo != null) {
            // Update in place
            sm.updateShortcuts(listOf(sinfo))
            sm.enableShortcuts(listOf(computer.uuid))
        }

        // Reap shortcuts to make space for this if it's new
        // NOTE: This CAN'T be an else on the above if, because it's
        // possible that we have an existing shortcut but it's not a dynamic one.
        if (!isExistingDynamicShortcut(computer.uuid)) {
            // To avoid a random carousel of shortcuts popping in and out based on polling status,
            // we only add shortcuts if it's not at the limit or the user made a conscious action
            // to interact with this PC.

            if (forceAdd) {
                // This should free an entry for us to add one below
                reapShortcutsForDynamicAdd()
            }

            // We still need to check the maximum shortcut count even after reaping,
            // because there's a possibility that it could be zero.
            if (sm.dynamicShortcuts.size < sm.maxShortcutCountPerActivity) {
                // Add a shortcut if there is room
                sm.addDynamicShortcuts(listOf(sinfo))
            }
        }

        if (newlyPaired) {
            // Avoid hammering the channel API for each computer poll because it will throttle us
            tvChannelHelper.createTvChannel(computer)
            tvChannelHelper.requestChannelOnHomeScreen(computer)
        }
    }

    private fun getShortcutIdForGame(computer: ComputerDetails, app: NvApp): String =
        "${computer.uuid}${app.appId}"

    fun createPinnedGameShortcut(
        computer: ComputerDetails,
        app: NvApp,
        iconBits: Bitmap?,
    ): Boolean =
        if (sm.isRequestPinShortcutSupported) {
            val appIcon =
                if (iconBits != null) {
                    Icon.createWithAdaptiveBitmap(iconBits)
                } else {
                    Icon.createWithResource(context, R.mipmap.ic_pc_scut)
                }

            val sInfo =
                ShortcutInfo.Builder(context, getShortcutIdForGame(computer, app))
                    .setIntent(ServerHelper.createAppShortcutIntent(context, computer, app))
                    .setShortLabel("${app.appName} (${computer.name})")
                    .setIcon(appIcon)
                    .build()

            sm.requestPinShortcut(sInfo, null)
        } else {
            false
        }

    fun disableComputerShortcut(computer: ComputerDetails, reason: CharSequence?) {
        tvChannelHelper.deleteChannel(computer)
        // Delete the computer shortcut itself
        if (getInfoForId(computer.uuid) != null) {
            sm.disableShortcuts(listOf(computer.uuid), reason)
        }

        // Delete all associated app shortcuts too
        val shortcuts = getAllShortcuts()
        val appShortcutIds = mutableListOf<String>()
        for (info in shortcuts) {
            if (info.id.startsWith(checkNotNull(computer.uuid))) {
                appShortcutIds.add(info.id)
            }
        }
        sm.disableShortcuts(appShortcutIds, reason)
    }

    fun disableAppShortcut(computer: ComputerDetails, app: NvApp, reason: CharSequence?) {
        tvChannelHelper.deleteProgram(computer, app)
        val id = getShortcutIdForGame(computer, app)
        if (getInfoForId(id) != null) {
            sm.disableShortcuts(listOf(id), reason)
        }
    }

    fun enableAppShortcut(computer: ComputerDetails, app: NvApp) {
        val id = getShortcutIdForGame(computer, app)
        if (getInfoForId(id) != null) {
            sm.enableShortcuts(listOf(id))
        }
    }
}
