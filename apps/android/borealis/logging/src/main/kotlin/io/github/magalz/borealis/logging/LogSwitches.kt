package io.github.magalz.borealis.logging

/**
 * The two logging switches the user controls: verbose logging and address masking. Whoever stores
 * them (the DataStore-backed settings repository, ADR 0055) implements this, so the diagnostic
 * bundle reads them without knowing where they live. Reads are synchronous and cheap: an
 * implementation backed by DataStore serves a cached value instead of waiting for the disk.
 */
interface LogSwitches {
    val verboseLogging: Boolean
    val maskAddresses: Boolean
}
