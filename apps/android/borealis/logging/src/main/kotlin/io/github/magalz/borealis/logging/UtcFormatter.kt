package io.github.magalz.borealis.logging

import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * A formatter for [pattern] that reads every instant as UTC, so that log lines, crash report names
 * and bundle names agree on the zone whatever the device uses.
 */
fun utcFormatter(pattern: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(pattern).withZone(ZoneOffset.UTC)
