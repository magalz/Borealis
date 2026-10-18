package io.github.magalz.borealis.host

import android.content.Context

/**
 * [Context.getString] with the format [args] in a list, the form the text builders take so that a
 * test can stand in for the resources.
 */
@Suppress("SpreadOperator") // Java varargs need the array spread; a few short strings, built once.
internal fun Context.getFormattedString(id: Int, args: List<Any>): String =
    getString(id, *args.toTypedArray())
