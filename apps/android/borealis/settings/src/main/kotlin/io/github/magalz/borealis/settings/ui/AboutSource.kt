package io.github.magalz.borealis.settings.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import io.github.magalz.borealis.settings.LegalTexts
import io.github.magalz.borealis.settings.R

/**
 * What the About rows need from the system: the version, how to open a link, and the licence texts.
 * A test passes its own.
 *
 * @property versionName the version name of the app, or `null` when the system does not know it.
 * @property commit the commit that the app was built from.
 * @property openLink opens a link in an app and returns whether an app took it.
 * @property loadLicenses the bundled `NOTICE` and `LICENSE` texts, or `null` when the build has
 *   none.
 */
@Immutable
class AboutSource(
    val versionName: String?,
    val commit: String,
    val openLink: (String) -> Boolean,
    val loadLicenses: () -> String?,
)

/** The [AboutSource] of this app: its version, the browser and the bundled licence assets. */
@Composable
fun rememberAboutSource(): AboutSource {
    val context = LocalContext.current
    val commit = stringResource(R.string.borealis_git_sha)
    return remember(context, commit) {
        AboutSource(
            versionName = versionName(context),
            commit = commit,
            openLink = { url -> openInApp(context, url) },
            loadLicenses = { LegalTexts.load { context.assets.open(it) } },
        )
    }
}

/**
 * The version name of the app, or `null` when the system does not know it. The About rows and the
 * footer of the Library rail read the version from here.
 */
fun versionName(context: Context): String? =
    try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

/**
 * Opens [url] in the app that takes it, in a task of its own; false when none does (a Google TV
 * often has no browser). In the task of Borealis a browser that ignores Back would keep the Library
 * out of reach until the task restarted.
 */
private fun openInApp(context: Context, url: String): Boolean =
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
