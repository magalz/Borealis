package io.github.magalz.borealis.settings.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.github.magalz.borealis.settings.audit.AuditRoute
import io.github.magalz.borealis.settings.privacy.PrivacyPane
import io.github.magalz.borealis.settings.privacy.PrivacyScreen
import io.github.magalz.borealis.settings.transfer.SendToPhoneDialog
import io.github.magalz.borealis.settings.ui.SettingsGroupId
import io.github.magalz.borealis.settings.ui.SettingsRoute
import java.io.File
import kotlinx.serialization.Serializable

/**
 * The Settings screen (UC-009). A TV or a handheld shows Privacy and diagnostics as one of its
 * groups, and [group] is the one that is selected when the screen opens.
 */
@Serializable data class Settings(val group: SettingsGroupId = SettingsGroupId.STREAM)

/** Privacy and diagnostics as a screen of its own, for a phone (UC-006, UC-007). */
@Serializable data object Privacy

/** The Audit trail viewer (UC-007). */
@Serializable data object Audit

/** Whether this is one of the screens of [settingsGraph]; the Library marks its rail with it. */
fun NavDestination?.isSettingsDestination(): Boolean =
    this != null && (hasRoute<Settings>() || hasRoute<Privacy>() || hasRoute<Audit>())

/**
 * Settings, Privacy and diagnostics and the Audit trail as destinations of the host's graph.
 *
 * - A phone: Settings leads to Privacy, which leads to Audit trail. Back goes back one step.
 * - A TV or a handheld: Settings has Privacy and diagnostics as a group, and the Audit trail opens
 *   from it. Send to phone, offered on a TV, is a dialog over the screen that offered it, so the
 *   Saved dialog of the export stays open under it.
 *
 * The host puts the screens where it wants them with `navigate(Settings(...))`, `navigate(Privacy)`
 * and `navigate(Audit)`, and draws no rail inside them.
 */
fun NavGraphBuilder.settingsGraph(navController: NavController) {
    composable<Settings> { entry ->
        val group = entry.toRoute<Settings>().group
        WithSendToPhone { onSendToPhone ->
            SettingsRoute(
                onBack = { navController.leave(entry) },
                onOpenPrivacy = { navController.show(Privacy) },
                privacyPane = {
                    PrivacyPane(
                        onSendToPhone = onSendToPhone,
                        onOpenAudit = { navController.show(Audit) },
                    )
                },
                initialGroup = group,
            )
        }
    }
    composable<Privacy> { entry ->
        WithSendToPhone { onSendToPhone ->
            PrivacyScreen(
                onBack = { navController.leave(entry) },
                onSendToPhone = onSendToPhone,
                onOpenAudit = { navController.show(Audit) },
            )
        }
    }
    composable<Audit> { entry -> AuditRoute(onBack = { navController.leave(entry) }) }
}

/** Goes to [route] unless it is on top already, which a double tap would otherwise stack twice. */
private fun NavController.show(route: Any) {
    navigate(route) { launchSingleTop = true }
}

/** Goes back from [entry]. A screen can end more than once in a frame; only the first counts. */
private fun NavController.leave(entry: NavBackStackEntry) {
    if (currentBackStackEntry == entry) popBackStack()
}

/** The bundle that Send to phone is offering, and the command that copies it with adb. */
private class PendingSend(val bundlePath: String, val adbPullCommand: String)

private val PendingSendSaver =
    listSaver<PendingSend?, String>(
        save = { listOfNotNull(it?.bundlePath, it?.adbPullCommand) },
        restore = { if (it.size == 2) PendingSend(it[0], it[1]) else null },
    )

/**
 * [content] with a way to offer Send to phone: calling `onSendToPhone` shows the dialog over what
 * [content] draws, and closing the dialog removes it, leaving [content] as it was.
 */
@Composable
private fun WithSendToPhone(content: @Composable (onSendToPhone: (File, String) -> Unit) -> Unit) {
    var pending by
        rememberSaveable(stateSaver = PendingSendSaver) { mutableStateOf<PendingSend?>(null) }
    content { bundle, command -> pending = PendingSend(bundle.path, command) }
    pending?.let {
        SendToPhoneDialog(
            bundle = File(it.bundlePath),
            adbPullCommand = it.adbPullCommand,
            onDismiss = { pending = null },
        )
    }
}
