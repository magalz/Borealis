package io.github.magalz.borealis.connection

import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * Makes `NativeLibrary("borealis_jni")` report that it loaded, so the host rules answer instead of
 * `null`. Together with [ShadowHostRules] this puts a stand-in for borealis-core under
 * `NativeHostRules` (a unit test has no `libborealis_jni.so`).
 */
@Implements(className = "io.github.magalz.borealis.nativecore.NativeLibrary")
class ShadowLoadedNativeLibrary {
    // The shadow replaces `load()`: the answer has to be a function, not a constant.
    @Suppress("unused", "FunctionOnlyReturningConstant") @Implementation fun load(): Boolean = true
}

/**
 * A stand-in for `launch.cpp` of borealis-core, over the JNI entry points that `NativeHostRules`
 * calls. It keeps the categories and the number of retries of host contract 7.5 but waits
 * [RETRY_DELAY_MS] between retries instead of seconds. It covers what the connection tests need:
 * `400` (an app is already running) on `/launch`, `409` and `503` (retry), `503` "no running app to
 * resume" on `/resume`; every other status is permanent. `launchFps` is the rule of the core.
 *
 * It is a class, not an object: the shadow of a class that is instantiated needs a public empty
 * constructor, and `CoreJni` is a Kotlin object.
 */
@Implements(className = "io.github.magalz.borealis.nativecore.CoreJni")
@Suppress("UtilityClassWithPublicConstructor") // The shadow needs the public empty constructor.
class ShadowHostRules {
    companion object {
        const val RETRY_DELAY_MS = 10
        const val PENDING_RETRIES = 4
        const val CAPTURE_RETRIES = 1

        // The ordinals of LaunchAction and LaunchCategory in borealis-core.
        private const val FAIL = 0
        private const val RETRY = 1
        private const val FALLBACK_TO_LAUNCH = 2
        private const val RESOLVE_CONFLICT = 3
        private const val PERMANENT = 0
        private const val PENDING = 1
        private const val CAPTURE_OR_DISPLAY = 4
        private const val NO_RUNNING_APP = 5
        private const val APP_RUNNING = 6
        private const val VERB_RESUME = 1
        private const val STATUS_APP_RUNNING = 400
        private const val STATUS_PENDING = 409
        private const val STATUS_UNAVAILABLE = 503
        private const val MAX_LAUNCH_FPS = 60

        @Suppress("unused")
        @Implementation
        @JvmStatic
        fun decideLaunch(verb: Int, status: Int, message: ByteArray?, attempts: Int): IntArray {
            val text = message?.toString(Charsets.UTF_8).orEmpty().lowercase()
            val resume = verb == VERB_RESUME
            return when {
                status == STATUS_APP_RUNNING && !resume && "an app is already running" in text ->
                    decision(RESOLVE_CONFLICT, APP_RUNNING, 0, 0)
                status == STATUS_PENDING -> retrying(PENDING, PENDING_RETRIES, attempts)
                status != STATUS_UNAVAILABLE -> decision(FAIL, PERMANENT, 0, 0)
                resume && "no running app to resume" in text ->
                    decision(FALLBACK_TO_LAUNCH, NO_RUNNING_APP, 0, 0)
                else -> retrying(CAPTURE_OR_DISPLAY, CAPTURE_RETRIES, attempts)
            }
        }

        /**
         * `borealis_launch_fps`: the stream rate for a host that is not GFE, else the launch rate.
         */
        @Suppress("unused")
        @Implementation
        @JvmStatic
        fun launchFps(isGfe: Boolean, launchFps: Int, streamFps: Int): Int =
            when {
                !isGfe && streamFps > 0 -> streamFps
                launchFps > MAX_LAUNCH_FPS || launchFps < 0 -> 0
                else -> launchFps
            }

        private fun retrying(category: Int, retries: Int, attempts: Int): IntArray =
            if (attempts < retries) {
                decision(RETRY, category, RETRY_DELAY_MS, retries)
            } else {
                decision(FAIL, category, 0, retries)
            }

        /** `[status, action, delay_ms, category, max_attempts]`, with the OK status. */
        private fun decision(action: Int, category: Int, delayMs: Int, maxAttempts: Int) =
            intArrayOf(0, action, delayMs, category, maxAttempts)
    }
}
