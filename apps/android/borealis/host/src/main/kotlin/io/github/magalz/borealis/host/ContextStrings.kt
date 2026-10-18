package io.github.magalz.borealis.host

import android.content.Context

/**
 * [Context.getString] with the format [args] in a list, the form the text builders take so a test
 * can stand in for the resources. [Context.getString] takes Java varargs, so the array has to be
 * spread: the lists hold one to three short strings and a dialog text is built once, not per frame.
 */
@Suppress("SpreadOperator")
internal fun Context.getFormattedString(id: Int, args: List<Any>): String =
    getString(id, *args.toTypedArray())
