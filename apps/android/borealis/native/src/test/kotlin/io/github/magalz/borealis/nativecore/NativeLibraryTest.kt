package io.github.magalz.borealis.nativecore

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.logging.LogTag
import org.junit.After
import org.junit.Before
import org.junit.Test

class NativeLibraryTest {
    private val sink = RecordingSink()
    private val loaded = mutableListOf<String>()

    @Before
    fun setUp() {
        BorealisLog.initialize(listOf(sink))
    }

    @After
    fun tearDown() {
        BorealisLog.shutdown()
    }

    private fun library(failWith: Throwable? = null) =
        NativeLibrary("borealis_test") { name ->
            loaded += name
            failWith?.let { throw it }
        }

    @Test
    fun `a library that loads is usable and is loaded exactly once`() {
        val library = library()

        assertThat(library.load()).isTrue()
        assertThat(library.load()).isTrue()

        assertThat(loaded).containsExactly("borealis_test")
        assertThat(sink.records).isEmpty()
    }

    @Test
    fun `a missing library is reported once and never throws`() {
        val library = library(UnsatisfiedLinkError("dlopen failed: library not found"))

        assertThat(library.load()).isFalse()
        assertThat(library.load()).isFalse()

        assertThat(loaded).hasSize(1)
        val record = sink.records.single()
        assertThat(record.level).isEqualTo(LogLevel.ERROR)
        assertThat(record.tag).isEqualTo(LogTag.CORE)
        assertThat(record.fields).contains("library" to "borealis_test")
        assertThat(record.stackTrace).contains("UnsatisfiedLinkError")
    }

    @Test
    fun `a security failure while loading is contained`() {
        val library = library(SecurityException("denied"))

        assertThat(library.load()).isFalse()
        assertThat(sink.records.single().level).isEqualTo(LogLevel.ERROR)
    }

    @Test
    fun `a call returns the native result once the library is loaded`() {
        val library = library()

        assertThat(library.call("answer") { "0.1.0" }).isEqualTo("0.1.0")
    }

    @Test
    fun `a call is skipped and returns null when the library could not be loaded`() {
        val library = library(UnsatisfiedLinkError("missing"))
        var called = false

        val result =
            library.call("answer") {
                called = true
                "0.1.0"
            }

        assertThat(result).isNull()
        assertThat(called).isFalse()
    }

    @Test
    fun `a missing native symbol is reported with the operation and returns null`() {
        val library = library()

        val result = library.call<String>("version") { throw UnsatisfiedLinkError("no symbol") }

        assertThat(result).isNull()
        val record = sink.records.single()
        assertThat(record.level).isEqualTo(LogLevel.ERROR)
        assertThat(record.fields).contains("operation" to "version")
    }

    @Test
    fun `a native result of null is passed through without an error`() {
        val library = library()

        assertThat(library.call<String>("version") { null }).isNull()
        assertThat(sink.records).isEmpty()
    }
}
