package io.github.magalz.borealis.diagnostics.bundle

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Build
import android.preference.PreferenceManager
import io.github.magalz.borealis.diagnostics.R
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.serialization.Serializable

/**
 * `manifest.json` of the diagnostic bundle: who produced it, on what device, with which settings.
 */
@Serializable
data class BundleManifest(
    val app: AppInfo,
    val device: DeviceInfo,
    val createdUtc: String,
    val logSettings: LogSettingsInfo,
    /** Content that did not fit the size bound or could not be written; empty when complete. */
    val truncated: List<TruncatedEntry> = emptyList(),
)

@Serializable
data class AppInfo(
    val versionName: String,
    val versionCode: Long,
    val gitSha: String,
    val buildType: String,
)

@Serializable
data class DeviceInfo(
    val androidRelease: String,
    val sdk: Int,
    val manufacturer: String,
    val model: String,
    val abis: List<String>,
)

@Serializable data class LogSettingsInfo(val verbose: Boolean, val maskAddresses: Boolean)

/** One zip entry that is shorter than its source, or missing. */
@Serializable
data class TruncatedEntry(
    val name: String,
    /** `size-limit` or `section-error`. */
    val reason: String,
    val keptBytes: Long,
    val originalBytes: Long,
)

/** Reads the manifest fields from the running app and device. */
object ManifestCollector {
    // The keys of SettingsKeys in :borealis:settings, which depends on this module.
    private const val KEY_VERBOSE_LOGGING = "borealis_verbose_logging"
    private const val KEY_MASK_ADDRESSES = "borealis_mask_addresses"
    private const val UNKNOWN = "unknown"

    @Suppress("DEPRECATION")
    fun collect(context: Context, now: Instant = Instant.now()): BundleManifest {
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        return BundleManifest(
            app = appInfo(context, info.getOrNull()),
            device =
                DeviceInfo(
                    androidRelease = Build.VERSION.RELEASE,
                    sdk = Build.VERSION.SDK_INT,
                    manufacturer = Build.MANUFACTURER,
                    model = Build.MODEL,
                    abis = Build.SUPPORTED_ABIS.toList(),
                ),
            createdUtc = now.truncatedTo(ChronoUnit.SECONDS).toString(),
            logSettings =
                LogSettingsInfo(
                    verbose = preferences.getBoolean(KEY_VERBOSE_LOGGING, false),
                    maskAddresses = preferences.getBoolean(KEY_MASK_ADDRESSES, false),
                ),
        )
    }

    private fun appInfo(context: Context, info: PackageInfo?): AppInfo {
        val debuggable = info?.applicationInfo?.flags?.and(ApplicationInfo.FLAG_DEBUGGABLE) ?: 0
        return AppInfo(
            versionName = info?.versionName ?: UNKNOWN,
            versionCode = info?.longVersionCode ?: 0L,
            gitSha = context.getString(R.string.borealis_git_sha),
            buildType = if (debuggable != 0) "debug" else "release",
        )
    }
}
