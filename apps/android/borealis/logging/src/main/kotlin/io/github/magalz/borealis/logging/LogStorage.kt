package io.github.magalz.borealis.logging

/**
 * Names that the app, the settings screen and the diagnostic bundle must agree on (ADR 0050). They
 * live here, in the module every other Borealis module depends on, so they cannot drift apart.
 */
object LogStorage {
    /** Directory of the rotating log files and crash reports, under `noBackupFilesDir`. */
    const val DIRECTORY = "logs"

    /** SharedPreferences key of the "Verbose logging" switch. */
    const val VERBOSE_LOGGING_KEY = "borealis_verbose_logging"

    /** SharedPreferences key of the "Mask addresses" switch. */
    const val MASK_ADDRESSES_KEY = "borealis_mask_addresses"
}
