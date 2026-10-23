package com.limelight.nvstream.http

import java.io.IOException

class HostHttpResponseException(val errorCode: Int, private val errorMsg: String?) : IOException() {
    val errorMessage: String?
        get() = errorMsg

    override val message: String
        get() = "Host PC returned error: $errorMsg (Error code: $errorCode)"

    private companion object {
        private const val serialVersionUID = 1543508830807804222L
    }
}
