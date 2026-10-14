package io.github.magalz.borealis.nativecore

/**
 * The LuminalShine host rules of borealis-core (`core/include/borealis/host.h`). Every function
 * returns null when the native library is unavailable or rejects the input, so callers fall back to
 * the upstream Moonlight behaviour.
 */
interface HostRules {
    /**
     * Parses the `/serverinfo` `appversion` text and the `ServerCodecModeSupport` mask. [codecMask]
     * is the raw 32-bit pattern, so `0x80000000` is passed as [Int.MIN_VALUE]. An [appVersion]
     * longer than 64 UTF-8 bytes yields null.
     */
    fun parseCaps(appVersion: String?, codecMask: Int): HostCaps?

    /**
     * Decides what to do after `/launch` or `/resume` answered with a non-200 [status].
     * [attemptsInCategory] counts the retries already made in the decision's category; a negative
     * value is treated as [Int.MAX_VALUE] (the core reads it as an unsigned count, so it can only
     * mean "out of attempts"). [message] is truncated to 256 UTF-8 bytes.
     */
    fun decideLaunch(
        verb: LaunchVerb,
        status: Int,
        message: String?,
        attemptsInCategory: Int,
    ): LaunchDecision?

    /**
     * Classifies a `/pair` error the host answered with [status] and [message] (truncated to 256
     * UTF-8 bytes): restart the pairing once, tell the user to wait for the host, or fail with a
     * message for the category.
     */
    fun classifyPairing(status: Int, message: String?): PairDecision?

    /** The frame rate for the `/launch` `mode=WxHxFPS` parameter; never negative. */
    fun launchFps(isGfe: Boolean, launchFps: Int, streamFps: Int): Int?
}

/** Host software family, derived from the `appversion` text. */
enum class HostFamily {
    UNKNOWN,
    GFE,
    SUNSHINE,
}

/** PyroWave profiles a host advertises in `ServerCodecModeSupport`. */
enum class PyroWaveProfile {
    SDR_420,
    SDR_444,
    HDR_420,
    HDR_444,
}

/**
 * Parsed host capabilities.
 *
 * [version] always has four components; they are all 0 when [family] is [HostFamily.UNKNOWN] (the
 * core's own convention). [yuv444] is set when any 4:4:4 mode bit is present.
 */
data class HostCaps(
    val family: HostFamily,
    val version: List<Int>,
    val hevc: Boolean,
    val hevcMain10: Boolean,
    val av1: Boolean,
    val av1Main10: Boolean,
    val yuv444: Boolean,
    val pyroWaveProfiles: Set<PyroWaveProfile>,
)

/** The request that failed. */
enum class LaunchVerb {
    LAUNCH,
    RESUME,
}

/** What the caller does next. */
enum class LaunchAction {
    /** Stop and show the host message. */
    FAIL,

    /** Wait [LaunchDecision.delayMs], then repeat the same request. */
    RETRY,

    /** `/resume` found no app: call `/launch` instead. */
    FALLBACK_TO_LAUNCH,

    /** `/launch` found another app running: ask the user, never cancel it unasked. */
    RESOLVE_CONFLICT,
}

/** Failure category; retries are counted per category. */
enum class LaunchCategory {
    PERMANENT,
    PENDING,
    RESTORING_DISPLAY,
    GPU_NOT_READY,
    CAPTURE_OR_DISPLAY,
    NO_RUNNING_APP,

    /** 400 on `/launch`: an app is already running on the host. */
    APP_RUNNING,
}

/**
 * [delayMs] is 0 unless [action] is [LaunchAction.RETRY]; [maxAttempts] is the number of retries
 * allowed in [category] (0 for [LaunchCategory.PERMANENT], [LaunchCategory.NO_RUNNING_APP] and
 * [LaunchCategory.APP_RUNNING]).
 */
data class LaunchDecision(
    val action: LaunchAction,
    val delayMs: Int,
    val category: LaunchCategory,
    val maxAttempts: Int,
)

/** What the caller does after a pairing error. */
enum class PairAction {
    /** Stop and show a message for the category. */
    FAIL,

    /** The host dropped its session: explain, then pair again with a new PIN, once. */
    RESTART_ONCE,

    /** The host holds another session: tell the user to wait or restart the host. */
    WAIT_HOST_SESSION,
}

/** Which host pairing error it was; selects the message the user sees. */
enum class PairCategory {
    UNKNOWN,
    STALE_SESSION,
    EXCHANGE_INVALIDATED,
    HOST_RESET,
    IN_PROGRESS,
    IDENTITY_MISMATCH,
    TOO_MANY_PENDING,
    PEER_MISMATCH,
    SUPERSEDED,
}

data class PairDecision(val action: PairAction, val category: PairCategory)
