package io.github.magalz.borealis.host.pairing

import java.io.IOException
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Placeholder with the behaviour of the upstream `unpair()`: any HTTP 2xx counts as success. */
object BestEffortUnpair {
    @JvmStatic
    fun unpair(client: OkHttpClient, url: HttpUrl): Boolean =
        try {
            client.newCall(Request.Builder().url(url).get().build()).execute().use {
                it.body.string()
                it.isSuccessful
            }
        } catch (_: IOException) {
            false
        }
}
