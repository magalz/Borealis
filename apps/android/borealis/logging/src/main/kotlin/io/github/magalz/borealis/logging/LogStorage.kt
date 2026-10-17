package io.github.magalz.borealis.logging

/**
 * Names that the app and the diagnostic bundle must agree on (ADR 0050). They live here, in the
 * module every other Borealis module depends on, so they cannot drift apart.
 */
object LogStorage {
    /** Directory of the rotating log files and crash reports, under `noBackupFilesDir`. */
    const val DIRECTORY = "logs"
}
