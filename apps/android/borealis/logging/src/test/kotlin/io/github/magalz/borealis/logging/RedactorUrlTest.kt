package io.github.magalz.borealis.logging

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Query values of URLs are redacted, except for the allowlisted protocol parameters. */
class RedactorUrlTest {
    private val redactor = Redactor()

    @Test
    fun `query keys stay and identifying values are redacted`() {
        assertThat(redactor.redact("GET https://h:47984/serverinfo?uniqueid=ABC123&uuid=u-1 done"))
            .isEqualTo("GET https://h:47984/serverinfo?uniqueid=[REDACTED]&uuid=[REDACTED] done")
    }

    @Test
    fun `allowlisted protocol parameters keep their values`() {
        val url =
            "https://h:47984/launch?phrase=pairchallenge&updateState=1&mode=1920x1080x60" +
                "&additionalStates=1&sops=1&hdrMode=1&localAudioPlayMode=0&surroundAudioInfo=196610" +
                "&remoteControllersBitmap=1&gcmap=1&gcpersist=0&corever=1&AssetType=2&AssetIdx=0"

        assertThat(redactor.redact(url)).isEqualTo(url)
    }

    @Test
    fun `identifying and secret parameters are never allowlisted`() {
        val keys =
            listOf(
                "uniqueid",
                "uuid",
                "devicename",
                "appid",
                "rikey",
                "rikeyid",
                "salt",
                "clientcert",
                "clientchallenge",
                "serverchallengeresp",
                "clientpairingsecret",
                "pin",
                "somethingelse",
            )
        for (key in keys) {
            assertThat(redactor.redact("http://h/x?$key=value1"))
                .isEqualTo("http://h/x?$key=[REDACTED]")
        }
    }

    @Test
    fun `allowlisted keys match without regard to case`() {
        assertThat(redactor.redact("http://h/x?Mode=1920x1080x60&UPDATESTATE=1"))
            .isEqualTo("http://h/x?Mode=1920x1080x60&UPDATESTATE=1")
    }

    @Test
    fun `path and fragment are kept`() {
        assertThat(redactor.redact("https://h:47984/a/b?uuid=x&mode=1#top"))
            .isEqualTo("https://h:47984/a/b?uuid=[REDACTED]&mode=1#top")
    }

    @Test
    fun `a url without a query is untouched`() {
        val text = "see https://github.com/magalz/Borealis/blob/main/PRIVACY.md for details"
        assertThat(redactor.redact(text)).isEqualTo(text)
    }

    @Test
    fun `key value text outside a url is left to the other rules`() {
        val text = "uniqueid=0123456789ABCDEF devicename=roth"
        assertThat(redactor.redact(text)).isEqualTo(text)
    }

    @Test
    fun `credentials in a url are redacted`() {
        assertThat(redactor.redact("https://user:pw@h:1/x?mode=1"))
            .isEqualTo("https://[REDACTED]@h:1/x?mode=1")
    }

    @Test
    fun `an empty value is redacted too`() {
        assertThat(redactor.redact("http://h/x?uuid=&mode=1"))
            .isEqualTo("http://h/x?uuid=[REDACTED]&mode=1")
    }

    @Test
    fun `urls in field values are redacted`() {
        assertThat(redactor.redactField("url", "https://h/x?uniqueid=ABC123&mode=1"))
            .isEqualTo("https://h/x?uniqueid=[REDACTED]&mode=1")
    }

    @Test
    fun `a long hostile url is processed quickly`() {
        val hostile = "http://h/x?" + "a=1&".repeat(3_000) + "b".repeat(5_000)

        val start = System.nanoTime()
        redactor.redact(hostile)

        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(1_000)
    }

    @Test
    fun `fragment pairs are redacted like query pairs`() {
        assertThat(redactor.redact("https://h/p#uuid=x&mode=1&token2=y"))
            .isEqualTo("https://h/p#uuid=[REDACTED]&mode=1&token2=[REDACTED]")
        assertThat(redactor.redact("https://h/p?uuid=x#uniqueid=y"))
            .isEqualTo("https://h/p?uuid=[REDACTED]#uniqueid=[REDACTED]")
    }

    @Test
    fun `a fragment that is not a pair is kept`() {
        val text = "https://h/p?mode=1#top"
        assertThat(redactor.redact(text)).isEqualTo(text)
    }

    @Test
    fun `hostile 16 KB lines are processed within a tight budget`() {
        val hostile =
            listOf(
                "a-".repeat(8_192),
                "a.".repeat(8_192),
                "a-a.".repeat(4_096),
                "a+".repeat(8_192),
                "http://".repeat(2_300),
                "x://a@".repeat(2_700),
                "?a=1&".repeat(3_200),
            )
        for (masking in listOf(false, true)) {
            val subject = Redactor(maskAddresses = masking)
            for (line in hostile) {
                subject.redact(line) // warm-up
                val best =
                    (1..3).minOf {
                        val start = System.nanoTime()
                        subject.redact(line)
                        (System.nanoTime() - start) / 1_000_000
                    }
                assertThat(best).isLessThan(150)
            }
        }
    }
}
