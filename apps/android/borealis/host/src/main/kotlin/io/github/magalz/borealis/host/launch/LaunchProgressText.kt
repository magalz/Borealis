package io.github.magalz.borealis.host.launch

import android.content.Context

object LaunchProgressText {
    @JvmStatic
    fun format(
        context: Context,
        category: Int,
        attempt: Int,
        maxAttempts: Int,
        delayMs: Int,
    ): String = TODO("not implemented")

    internal fun format(
        getString: (Int, List<Any>) -> String,
        category: Int,
        attempt: Int,
        maxAttempts: Int,
        delayMs: Int,
    ): String = TODO("not implemented")
}
