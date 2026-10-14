package io.github.magalz.borealis.nativecore

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.logging.LogTag
import org.junit.After
import org.junit.Before
import org.junit.Test

class HostRulesBindingTest {
    private val sink = RecordingSink()

    private class FakeNative : HostNative {
        var appVersion: ByteArray? = null
        var codecMask = 0
        var verb = -1
        var status = -1
        var message: ByteArray? = null
        var attempts = -1
        var fpsArgs: Triple<Boolean, Int, Int>? = null
        var pairStatus = -1
        var pairMessage: ByteArray? = null
        var calls = 0

        var capsResult: IntArray? = intArrayOf(0, 2, 7, 1, 431, -1, 0x13, 0)
        var decisionResult: IntArray? = intArrayOf(0, 1, 2000, 2, 3)
        var fpsResult = 59
        var pairResult: IntArray? = intArrayOf(0, 1, 1)

        override fun parseCaps(appVersion: ByteArray?, codecMask: Int): IntArray? {
            calls++
            this.appVersion = appVersion
            this.codecMask = codecMask
            return capsResult
        }

        override fun decideLaunch(
            verb: Int,
            status: Int,
            message: ByteArray?,
            attempts: Int,
        ): IntArray? {
            calls++
            this.verb = verb
            this.status = status
            this.message = message
            this.attempts = attempts
            return decisionResult
        }

        override fun classifyPairing(status: Int, message: ByteArray?): IntArray? {
            calls++
            pairStatus = status
            pairMessage = message
            return pairResult
        }

        override fun launchFps(isGfe: Boolean, launchFps: Int, streamFps: Int): Int {
            calls++
            fpsArgs = Triple(isGfe, launchFps, streamFps)
            return fpsResult
        }
    }

    private val fake = FakeNative()
    private val rules = HostRulesBinding(NativeLibrary("borealis_test") {}, fake)

    @Before
    fun setUp() {
        BorealisLog.initialize(listOf(sink))
    }

    @After
    fun tearDown() {
        BorealisLog.shutdown()
    }

    @Test
    fun `parseCaps passes the UTF-8 appversion and the raw mask and decodes the result`() {
        val caps = rules.parseCaps("7.1.431.-1", 0x001C0301)!!

        assertThat(fake.appVersion).isEqualTo("7.1.431.-1".encodeToByteArray())
        assertThat(fake.codecMask).isEqualTo(0x001C0301)
        assertThat(caps.family).isEqualTo(HostFamily.SUNSHINE)
        assertThat(caps.version).containsExactly(7, 1, 431, -1).inOrder()
        assertThat(caps.hevc).isTrue()
        assertThat(caps.hevcMain10).isTrue()
        assertThat(caps.yuv444).isTrue()
    }

    @Test
    fun `parseCaps keeps the 0x80000000 bit pattern`() {
        rules.parseCaps("7.1.431.-1", 0x80000000u.toInt())

        assertThat(fake.codecMask).isEqualTo(Int.MIN_VALUE)
    }

    @Test
    fun `parseCaps passes an absent appversion as a null array`() {
        rules.parseCaps(null, 0)

        assertThat(fake.calls).isEqualTo(1)
        assertThat(fake.appVersion).isNull()
    }

    @Test
    fun `parseCaps accepts an appversion of exactly 64 bytes`() {
        val appVersion = "1".repeat(64)

        assertThat(rules.parseCaps(appVersion, 0)).isNotNull()
        assertThat(fake.appVersion).isEqualTo(appVersion.encodeToByteArray())
    }

    @Test
    fun `parseCaps rejects an appversion over 64 bytes without calling native or logging it`() {
        val appVersion = "9".repeat(65)

        assertThat(rules.parseCaps(appVersion, 0)).isNull()

        assertThat(fake.calls).isEqualTo(0)
        val record = sink.records.single()
        assertThat(record.level).isEqualTo(LogLevel.WARN)
        assertThat(record.tag).isEqualTo(LogTag.CORE)
        assertThat(record.message).doesNotContain(appVersion)
        assertThat(record.fields.map { it.second }).doesNotContain(appVersion)
    }

    @Test
    fun `parseCaps counts the appversion in bytes, not characters`() {
        assertThat(rules.parseCaps("é".repeat(33), 0)).isNull()
        assertThat(rules.parseCaps("é".repeat(32), 0)).isNotNull()
    }

    @Test
    fun `parseCaps is null when native reports an error status`() {
        fake.capsResult = intArrayOf(-2, 0, 0, 0, 0, 0, 0, 0)

        assertThat(rules.parseCaps("7.1.431.-1", 0)).isNull()
        assertThat(sink.records.single().level).isEqualTo(LogLevel.WARN)
    }

    @Test
    fun `decideLaunch passes the verb, status, message and attempts and decodes the result`() {
        val decision = rules.decideLaunch(LaunchVerb.RESUME, 503, "restoring", 1)!!

        assertThat(fake.verb).isEqualTo(1)
        assertThat(fake.status).isEqualTo(503)
        assertThat(fake.message).isEqualTo("restoring".encodeToByteArray())
        assertThat(fake.attempts).isEqualTo(1)
        assertThat(decision)
            .isEqualTo(
                LaunchDecision(
                    action = LaunchAction.RETRY,
                    delayMs = 2000,
                    category = LaunchCategory.RESTORING_DISPLAY,
                    maxAttempts = 3,
                )
            )
    }

    @Test
    fun `decideLaunch maps LAUNCH to 0 and an absent message to a null array`() {
        rules.decideLaunch(LaunchVerb.LAUNCH, 409, null, 0)

        assertThat(fake.verb).isEqualTo(0)
        assertThat(fake.message).isNull()
    }

    @Test
    fun `decideLaunch truncates the message to 256 bytes`() {
        rules.decideLaunch(LaunchVerb.LAUNCH, 503, "a".repeat(300), 0)

        assertThat(fake.message!!.size).isEqualTo(256)
    }

    @Test
    fun `decideLaunch clamps negative attempts to Int MAX_VALUE`() {
        rules.decideLaunch(LaunchVerb.LAUNCH, 409, null, -5)

        assertThat(fake.attempts).isEqualTo(Int.MAX_VALUE)
    }

    @Test
    fun `decideLaunch is null when native reports an error status`() {
        fake.decisionResult = intArrayOf(-1, 0, 0, 0, 0)

        assertThat(rules.decideLaunch(LaunchVerb.LAUNCH, 409, null, 0)).isNull()
    }

    @Test
    fun `classifyPairing passes the status and the UTF-8 message and decodes the result`() {
        val decision = rules.classifyPairing(400, "Out of order call to getservercert")

        assertThat(fake.pairStatus).isEqualTo(400)
        assertThat(fake.pairMessage).isEqualTo("Out of order call to getservercert".encodeToByteArray())
        assertThat(decision)
            .isEqualTo(PairDecision(PairAction.RESTART_ONCE, PairCategory.STALE_SESSION))
    }

    @Test
    fun `classifyPairing passes an absent message as a null array and truncates to 256 bytes`() {
        rules.classifyPairing(503, null)
        assertThat(fake.pairMessage).isNull()

        rules.classifyPairing(503, "a".repeat(300))
        assertThat(fake.pairMessage!!.size).isEqualTo(256)
    }

    @Test
    fun `classifyPairing is null when native reports an error status`() {
        fake.pairResult = intArrayOf(-1, 0, 0)

        assertThat(rules.classifyPairing(400, null)).isNull()
    }

    @Test
    fun `launchFps passes its arguments and returns the native value`() {
        assertThat(rules.launchFps(isGfe = false, launchFps = 60, streamFps = 59)).isEqualTo(59)

        assertThat(fake.fpsArgs).isEqualTo(Triple(false, 60, 59))
    }

    @Test
    fun `a native result of null is passed through`() {
        fake.capsResult = null
        fake.decisionResult = null
        fake.pairResult = null

        assertThat(rules.parseCaps("7.1.431.-1", 0)).isNull()
        assertThat(rules.decideLaunch(LaunchVerb.LAUNCH, 409, null, 0)).isNull()
        assertThat(rules.classifyPairing(400, null)).isNull()
    }

    @Test
    fun `every call returns null without touching native when the library cannot load`() {
        val unavailable =
            HostRulesBinding(
                NativeLibrary("borealis_test") { throw UnsatisfiedLinkError("missing") },
                fake,
            )

        assertThat(unavailable.parseCaps("7.1.431.-1", 0)).isNull()
        assertThat(unavailable.decideLaunch(LaunchVerb.LAUNCH, 409, null, 0)).isNull()
        assertThat(unavailable.classifyPairing(400, null)).isNull()
        assertThat(unavailable.launchFps(true, 60, 60)).isNull()

        assertThat(fake.calls).isEqualTo(0)
    }

    @Test
    fun `a missing native symbol returns null instead of throwing`() {
        val broken =
            object : HostNative {
                override fun parseCaps(appVersion: ByteArray?, codecMask: Int): IntArray? =
                    throw UnsatisfiedLinkError("no symbol")

                override fun decideLaunch(
                    verb: Int,
                    status: Int,
                    message: ByteArray?,
                    attempts: Int,
                ): IntArray? = throw UnsatisfiedLinkError("no symbol")

                override fun classifyPairing(status: Int, message: ByteArray?): IntArray? =
                    throw UnsatisfiedLinkError("no symbol")

                override fun launchFps(isGfe: Boolean, launchFps: Int, streamFps: Int): Int =
                    throw UnsatisfiedLinkError("no symbol")
            }
        val rules = HostRulesBinding(NativeLibrary("borealis_test") {}, broken)

        assertThat(rules.parseCaps("7.1.431.-1", 0)).isNull()
        assertThat(rules.decideLaunch(LaunchVerb.LAUNCH, 409, null, 0)).isNull()
        assertThat(rules.classifyPairing(400, null)).isNull()
        assertThat(rules.launchFps(true, 60, 60)).isNull()
    }

    @Test
    fun `NativeHostRules is null instead of a crash when the native library is missing on the JVM`() {
        assertThat(NativeHostRules.parseCaps("7.1.431.-1", 0)).isNull()
        assertThat(NativeHostRules.decideLaunch(LaunchVerb.LAUNCH, 409, null, 0)).isNull()
        assertThat(NativeHostRules.classifyPairing(400, null)).isNull()
        assertThat(NativeHostRules.launchFps(false, 60, 60)).isNull()
    }
}
