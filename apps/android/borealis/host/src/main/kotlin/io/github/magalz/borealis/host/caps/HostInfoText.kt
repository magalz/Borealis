package io.github.magalz.borealis.host.caps

import android.content.Context

object HostInfoText {
    @JvmStatic
    fun appendTo(context: Context, details: String, info: BorealisHostInfo?): String =
        TODO("not implemented")

    internal fun appendTo(
        getString: (Int, List<Any>) -> String,
        details: String,
        info: BorealisHostInfo?,
    ): String = TODO("not implemented")
}
