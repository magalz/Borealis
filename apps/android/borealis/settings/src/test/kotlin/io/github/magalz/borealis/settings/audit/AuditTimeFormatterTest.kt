package io.github.magalz.borealis.settings.audit

import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.ZoneId
import java.util.Locale
import org.junit.Test

/** The times of the list: the clock, the time zone and the locale come from the device. */
class AuditTimeFormatterTest {
    private val formatter = fixedFormatter()

    @Test
    fun `a time of today shows the hour and the minute`() {
        assertThat(formatter.short(at("2026-10-16T20:42:00Z"))).isEqualTo("20:42")
    }

    @Test
    fun `an earlier day shows the weekday and the time`() {
        // 2026-10-12 is a Monday.
        assertThat(formatter.short(at("2026-10-12T18:02:00Z"))).isEqualTo("Mon 18:02")
    }

    @Test
    fun `the evening before is not today`() {
        assertThat(formatter.short(at("2026-10-15T23:59:00Z"))).isEqualTo("Thu 23:59")
    }

    @Test
    fun `the first minute of today is today`() {
        assertThat(formatter.short(at("2026-10-16T00:00:00Z"))).isEqualTo("00:00")
    }

    @Test
    fun `the time zone of the device decides what today is`() {
        val tokyo = AuditTimeFormatter({ Clock.fixed(NOW, ZoneId.of("Asia/Tokyo")) }, { Locale.US })

        // 20:50 UTC is 05:50 on Saturday the 17th in Tokyo, where 14:00 UTC is 23:00 on Friday.
        assertThat(tokyo.short(at("2026-10-16T14:00:00Z"))).isEqualTo("Fri 23:00")
        assertThat(formatter.short(at("2026-10-16T14:00:00Z"))).isEqualTo("14:00")
    }

    @Test
    fun `the locale of the device names the weekday`() {
        val german = AuditTimeFormatter({ Clock.fixed(NOW, ZoneId.of("UTC")) }, { Locale.GERMANY })

        assertThat(german.short(at("2026-10-12T18:02:00Z"))).startsWith("Mo")
        assertThat(german.short(at("2026-10-12T18:02:00Z"))).endsWith("18:02")
    }

    @Test
    fun `the full time has the date and the seconds`() {
        assertThat(formatter.full(at("2026-10-12T18:02:09Z"))).isEqualTo("12 Oct 2026 18:02:09")
    }
}
