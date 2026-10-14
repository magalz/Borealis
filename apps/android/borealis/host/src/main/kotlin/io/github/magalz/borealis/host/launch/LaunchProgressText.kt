package io.github.magalz.borealis.host.launch

import android.content.Context
import io.github.magalz.borealis.host.R
import io.github.magalz.borealis.nativecore.LaunchCategory

/** The spinner message while the client waits to repeat a `/launch` or `/resume` request. */
object LaunchProgressText {
    private const val MILLIS_PER_SECOND = 1000

    /**
     * "Another device is starting a stream on this host. Retrying in 2 s (attempt 2 of 4)." The
     * [category] is the ordinal of `LaunchCategory` the listener received.
     */
    @JvmStatic
    fun format(
        context: Context,
        category: Int,
        attempt: Int,
        maxAttempts: Int,
        delayMs: Int,
    ): String =
        format(
            { id, args -> context.getString(id, *args.toTypedArray()) },
            category,
            attempt,
            maxAttempts,
            delayMs,
        )

    internal fun format(
        getString: (Int, List<Any>) -> String,
        category: Int,
        attempt: Int,
        maxAttempts: Int,
        delayMs: Int,
    ): String {
        val seconds = (delayMs + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND
        // Plain digits: %d would group or localize numbers the way a count never should be here.
        val args = listOf(seconds.toString(), attempt.toString(), maxAttempts.toString())
        return getString(textFor(LaunchCategory.entries.getOrNull(category)), args)
    }

    private fun textFor(category: LaunchCategory?): Int =
        when (category) {
            LaunchCategory.PENDING -> R.string.launch_retry_pending
            LaunchCategory.RESTORING_DISPLAY -> R.string.launch_retry_restoring_display
            LaunchCategory.GPU_NOT_READY -> R.string.launch_retry_gpu_not_ready
            LaunchCategory.CAPTURE_OR_DISPLAY -> R.string.launch_retry_capture_or_display
            else -> R.string.launch_retry_generic
        }
}
