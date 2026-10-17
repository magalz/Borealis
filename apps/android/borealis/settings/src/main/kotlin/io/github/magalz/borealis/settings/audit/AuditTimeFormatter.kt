package io.github.magalz.borealis.settings.audit

import java.time.Clock
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * The times of the audit trail in the device's time zone and in [locale], which is English because
 * the app is (ADR 0055): a weekday in the device language would be the only text that is not. The
 * [clock] is read at each call, so a change of time zone shows in the next list. The patterns are
 * built once: a list of 2,000 entries formats 2,000 times, from any thread.
 */
class AuditTimeFormatter(private val clock: () -> Clock, locale: Locale) {
    @Inject constructor() : this({ Clock.systemDefaultZone() }, Locale.ENGLISH)

    private class Patterns(locale: Locale) {
        val today: DateTimeFormatter = DateTimeFormatter.ofPattern(TODAY, locale)
        val earlierDay: DateTimeFormatter = DateTimeFormatter.ofPattern(EARLIER_DAY, locale)
        val full: DateTimeFormatter = DateTimeFormatter.ofPattern(FULL, locale)
    }

    private val patterns = Patterns(locale)

    /** `HH:mm` for a time of today, and the weekday and `HH:mm` for an earlier day. */
    fun short(timestampMillis: Long): String {
        val now = clock()
        val time = Instant.ofEpochMilli(timestampMillis).atZone(now.zone)
        val sameDay = time.toLocalDate() == ZonedDateTime.now(now).toLocalDate()
        return (if (sameDay) patterns.today else patterns.earlierDay).format(time)
    }

    /** The date and the time with seconds, for the detail of an entry. */
    fun full(timestampMillis: Long): String =
        patterns.full.format(Instant.ofEpochMilli(timestampMillis).atZone(clock().zone))

    private companion object {
        const val TODAY = "HH:mm"
        const val EARLIER_DAY = "EEE HH:mm"
        const val FULL = "d MMM yyyy HH:mm:ss"
    }
}
