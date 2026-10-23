package io.github.magalz.borealis.hostmodel

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.http.NvApp
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Characterizes the app of the inherited host model: what it did before the Kotlin conversion. */
class NvAppTest {
    private val logger = Logger.getLogger("com.limelight.LimeLog")
    private val records = mutableListOf<LogRecord>()
    private val handler =
        object : Handler() {
            override fun publish(record: LogRecord) {
                records += record
            }

            override fun flush() = Unit

            override fun close() = Unit
        }

    @Before
    fun listen() {
        logger.addHandler(handler)
    }

    @After
    fun stopListening() {
        logger.removeHandler(handler)
    }

    @Test
    fun `an app made without arguments has an empty name and is not initialized`() {
        val app = NvApp()

        assertThat(app.appName).isEmpty()
        assertThat(app.appId).isEqualTo(0)
        assertThat(app.isHdrSupported).isFalse()
        assertThat(app.isInitialized).isFalse()
    }

    @Test
    fun `an app made with a name has no id and is not initialized`() {
        val app = NvApp("Steam")

        assertThat(app.appName).isEqualTo("Steam")
        assertThat(app.appId).isEqualTo(0)
        assertThat(app.isHdrSupported).isFalse()
        assertThat(app.isInitialized).isFalse()
    }

    @Test
    fun `an app made with a missing name has a missing name`() {
        assertThat(NvApp(null).appName).isNull()
    }

    @Test
    fun `an app made with a name, an id and HDR is initialized`() {
        val app = NvApp("Steam", 7, true)

        assertThat(app.appName).isEqualTo("Steam")
        assertThat(app.appId).isEqualTo(7)
        assertThat(app.isHdrSupported).isTrue()
        assertThat(app.isInitialized).isTrue()
    }

    @Test
    fun `an app made with the id 0 and no HDR is initialized too`() {
        val app = NvApp("", 0, false)

        assertThat(app.appId).isEqualTo(0)
        assertThat(app.isHdrSupported).isFalse()
        assertThat(app.isInitialized).isTrue()
    }

    @Test
    fun `the name can be changed, also to nothing, and does not initialize the app`() {
        val app = NvApp()

        app.appName = "Desktop"
        assertThat(app.appName).isEqualTo("Desktop")

        app.appName = null
        assertThat(app.appName).isNull()
        assertThat(app.isInitialized).isFalse()
    }

    @Test
    fun `the HDR flag can be changed and does not initialize the app`() {
        val app = NvApp()

        app.isHdrSupported = true

        assertThat(app.isHdrSupported).isTrue()
        assertThat(app.isInitialized).isFalse()
    }

    @Test
    fun `setting the id as a number initializes the app`() {
        val app = NvApp()

        app.appId = 881448767

        assertThat(app.appId).isEqualTo(881448767)
        assertThat(app.isInitialized).isTrue()
    }

    @Test
    fun `an id that is negative or zero is accepted`() {
        val app = NvApp()

        app.appId = -5
        assertThat(app.appId).isEqualTo(-5)

        app.appId = 0
        assertThat(app.appId).isEqualTo(0)
        assertThat(app.isInitialized).isTrue()
    }

    @Test
    fun `setting the id as text parses it and initializes the app`() {
        val app = NvApp()

        app.setAppId("42")

        assertThat(app.appId).isEqualTo(42)
        assertThat(app.isInitialized).isTrue()
        assertThat(records).isEmpty()
    }

    @Test
    fun `the id as text may have a sign`() {
        val app = NvApp()

        app.setAppId("-7")
        assertThat(app.appId).isEqualTo(-7)

        app.setAppId("+9")
        assertThat(app.appId).isEqualTo(9)
    }

    @Test
    fun `an id as text that is not a number is logged and ignored`() {
        for (text in listOf("abc", "", " 42", "42 ", "4.2", "2147483648")) {
            records.clear()
            val app = NvApp()

            app.setAppId(text)

            assertThat(app.appId).isEqualTo(0)
            assertThat(app.isInitialized).isFalse()
            assertThat(records.map { it.level to it.message })
                .containsExactly(Level.WARNING to "Malformed app ID: $text")
        }
    }

    @Test
    fun `a missing id as text is logged and ignored`() {
        val app = NvApp()

        app.setAppId(null as String?)

        assertThat(app.appId).isEqualTo(0)
        assertThat(app.isInitialized).isFalse()
        assertThat(records.map { it.level to it.message })
            .containsExactly(Level.WARNING to "Malformed app ID: null")
    }

    @Test
    fun `an id as text that is not a number leaves the id and the initialized flag as they were`() {
        val app = NvApp("Steam", 7, false)

        app.setAppId("seven")

        assertThat(app.appId).isEqualTo(7)
        assertThat(app.isInitialized).isTrue()
    }

    @Test
    fun `an app shows its name, whether it has HDR, and its id`() {
        assertThat(NvApp("Steam", 7, true).toString())
            .isEqualTo("Name: Steam\nHDR Supported: Yes\nID: 7\n")
        assertThat(NvApp("Desktop", 8, false).toString())
            .isEqualTo("Name: Desktop\nHDR Supported: Unknown\nID: 8\n")
    }

    @Test
    fun `an app made without arguments, or without a name, shows what it has`() {
        assertThat(NvApp().toString()).isEqualTo("Name: \nHDR Supported: Unknown\nID: 0\n")
        assertThat(NvApp(null).toString()).isEqualTo("Name: null\nHDR Supported: Unknown\nID: 0\n")
    }
}
