package io.github.magalz.borealis.settings.privacy

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag

/**
 * False when the system refused the share sheet: nothing handles the chooser, or the system does
 * not allow it. Any other failure is a bug and is not hidden.
 */
internal fun tryShare(startShare: (Intent) -> Unit, chooser: Intent): Boolean =
    try {
        startShare(chooser)
        true
    } catch (e: ActivityNotFoundException) {
        BorealisLog.e(LogTag.APP, "could not start the diagnostic share", e)
        false
    } catch (e: SecurityException) {
        BorealisLog.e(LogTag.APP, "could not start the diagnostic share", e)
        false
    }

/** Starts the share sheet from the current context. */
@Composable
internal fun rememberShareStarter(): (Intent) -> Unit {
    val context = LocalContext.current
    return remember(context) { { chooser -> context.startShareSheet(chooser) } }
}

/**
 * Starts [chooser] from the activity behind this context, if there is one; a context with no
 * activity needs a task of its own.
 */
internal fun Context.startShareSheet(chooser: Intent) {
    if (findActivity() == null) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(chooser)
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
