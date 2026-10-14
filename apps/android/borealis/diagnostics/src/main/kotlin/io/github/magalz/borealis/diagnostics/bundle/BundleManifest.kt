package io.github.magalz.borealis.diagnostics.bundle

import android.content.Context
import java.time.Instant
import kotlinx.serialization.Serializable

/** `manifest.json` of the diagnostic bundle: who produced it, on what device, with which settings. */
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
    fun collect(context: Context, now: Instant = Instant.now()): BundleManifest = TODO()
}
