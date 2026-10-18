package io.github.magalz.borealis.logging

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.util.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test

class UtcFormatterTest {
    private val original = TimeZone.getDefault()

    @Before fun deviceIsInTokyo() = TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))

    @After fun restoreZone() = TimeZone.setDefault(original)

    @Test
    fun `an instant is written in UTC whatever the device zone is`() {
        val formatter = utcFormatter("yyyyMMdd-HHmmss")

        assertThat(formatter.format(Instant.parse("2023-11-14T22:13:20Z")))
            .isEqualTo("20231114-221320")
    }

    @Test
    fun `the pattern decides the fields`() {
        val formatter = utcFormatter("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

        assertThat(formatter.format(Instant.parse("2023-11-14T22:13:20.123Z")))
            .isEqualTo("2023-11-14T22:13:20.123Z")
    }
}
