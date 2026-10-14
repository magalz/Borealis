package io.github.magalz.borealis.nativecore

import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag

/** Longest `appversion` the C ABI accepts, in UTF-8 bytes (`BOREALIS_HOST_APPVERSION_MAX_LEN`). */
internal const val APP_VERSION_MAX_BYTES = 64

/** Longest host message the C ABI accepts, in UTF-8 bytes (`BOREALIS_HOST_MESSAGE_MAX_LEN`). */
internal const val MESSAGE_MAX_BYTES = 256

/**
 * Pure encoding and decoding between the Kotlin types and the primitives the JNI carries.
 *
 * The JNI answers with an int array whose first element is the `BOREALIS_*` status:
 * - caps: `[status, family, v0, v1, v2, v3, codec_flags, pyrowave_profiles]`
 * - launch decision: `[status, action, delay_ms, category, max_attempts]`
 *
 * The enum values of `host.h` equal the ordinals of [HostFamily], [LaunchAction] and
 * [LaunchCategory], and [LaunchVerb.ordinal] is the verb value.
 */
internal object HostMarshalling {
    private const val STATUS = 0
    private const val STATUS_OK = 0

    private const val CAPS_FAMILY = 1
    private const val CAPS_VERSION_FIRST = 2
    private const val CAPS_VERSION_LAST = 5
    private const val CAPS_CODEC_FLAGS = 6
    private const val CAPS_PYROWAVE_PROFILES = 7

    private const val DECISION_ACTION = 1
    private const val DECISION_DELAY_MS = 2
    private const val DECISION_CATEGORY = 3
    private const val DECISION_MAX_ATTEMPTS = 4

    private const val CAPS_SIZE = 8
    private const val DECISION_SIZE = 5
    private const val UTF8_CONTINUATION_MASK = 0xC0
    private const val UTF8_CONTINUATION = 0x80

    private const val CODEC_HEVC = 0x01
    private const val CODEC_HEVC_MAIN10 = 0x02
    private const val CODEC_AV1 = 0x04
    private const val CODEC_AV1_MAIN10 = 0x08
    private const val CODEC_YUV444 = 0x10

    /**
     * The UTF-8 bytes of [message], cut to [MESSAGE_MAX_BYTES] without splitting a character, or
     * null for no message. An empty message is an empty array, not null: the core tells "absent"
     * from "empty".
     */
    fun encodeMessage(message: String?): ByteArray? {
        val bytes = message?.encodeToByteArray() ?: return null
        if (bytes.size <= MESSAGE_MAX_BYTES) return bytes
        var end = MESSAGE_MAX_BYTES
        while (end > 0 && isContinuation(bytes[end])) end--
        return bytes.copyOf(end)
    }

    /** The core reads the count as `uint32`: a negative value can only mean "out of attempts". */
    fun clampAttempts(attempts: Int): Int = if (attempts < 0) Int.MAX_VALUE else attempts

    fun decodeCaps(raw: IntArray?): HostCaps? {
        val wire = validated(raw, CAPS_SIZE, "caps") ?: return null
        val family = HostFamily.entries.getOrNull(wire[CAPS_FAMILY])
        if (family == null) {
            BorealisLog.w(LogTag.CORE, "native caps result has an unknown family")
            return null
        }
        val codec = wire[CAPS_CODEC_FLAGS]
        val pyroWave = wire[CAPS_PYROWAVE_PROFILES]
        return HostCaps(
            family = family,
            version = wire.slice(CAPS_VERSION_FIRST..CAPS_VERSION_LAST),
            hevc = codec and CODEC_HEVC != 0,
            hevcMain10 = codec and CODEC_HEVC_MAIN10 != 0,
            av1 = codec and CODEC_AV1 != 0,
            av1Main10 = codec and CODEC_AV1_MAIN10 != 0,
            yuv444 = codec and CODEC_YUV444 != 0,
            pyroWaveProfiles =
                PyroWaveProfile.entries.filterTo(mutableSetOf()) {
                    pyroWave and (1 shl it.ordinal) != 0
                },
        )
    }

    fun decodeDecision(raw: IntArray?): LaunchDecision? {
        val wire = validated(raw, DECISION_SIZE, "launch decision") ?: return null
        val action = LaunchAction.entries.getOrNull(wire[DECISION_ACTION])
        val category = LaunchCategory.entries.getOrNull(wire[DECISION_CATEGORY])
        if (action == null || category == null) {
            BorealisLog.w(LogTag.CORE, "native launch decision has an unknown action or category")
            return null
        }
        return LaunchDecision(
            action = action,
            delayMs = wire[DECISION_DELAY_MS],
            category = category,
            maxAttempts = wire[DECISION_MAX_ATTEMPTS],
        )
    }

    private fun isContinuation(byte: Byte) =
        byte.toInt() and UTF8_CONTINUATION_MASK == UTF8_CONTINUATION

    /** [raw] when it has [size] elements and an OK status, otherwise null after a warning. */
    private fun validated(raw: IntArray?, size: Int, what: String): IntArray? {
        if (raw == null || raw.size != size) {
            BorealisLog.w(LogTag.CORE, "native $what result missing or malformed")
            return null
        }
        if (raw[STATUS] != STATUS_OK) {
            BorealisLog.w(LogTag.CORE, "native $what call rejected", "status" to raw[STATUS])
            return null
        }
        return raw
    }
}
