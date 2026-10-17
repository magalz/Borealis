package io.github.magalz.borealis.settings.audit

import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * The times of the audit trail in the device's time zone and locale. [clock] and [locale] are read
 * at each call, so a change of either shows in the next list.
 */
class AuditTimeFormatter(private val clock: () -> Clock, private val locale: () -> Locale) {
    @Inject constructor() : this({ Clock.systemDefaultZone() }, { Locale.getDefault() })

    /** `HH:mm` for a time of today, and the weekday and `HH:mm` for an earlier day. */
    fun short(timestampMillis: Long): String {
        val now = clock()
        val time = Instant.ofEpochMilli(timestampMillis).atZone(now.zone)
        val pattern =
            if (time.toLocalDate() == now.instant().atZone(now.zone).toLocalDate()) {
                TODAY
            } else {
                EARLIER_DAY
            }
        return DateTimeFormatter.ofPattern(pattern, locale()).format(time)
    }

    /** The date and the time with seconds, for the detail of an entry. */
    fun full(timestampMillis: Long): String =
        DateTimeFormatter.ofPattern(FULL, locale())
            .format(Instant.ofEpochMilli(timestampMillis).atZone(clock().zone))

    private companion object {
        const val TODAY = "HH:mm"
        const val EARLIER_DAY = "EEE HH:mm"
        const val FULL = "d MMM yyyy HH:mm:ss"
    }
}
