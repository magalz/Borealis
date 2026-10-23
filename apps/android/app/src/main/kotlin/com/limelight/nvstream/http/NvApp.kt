package com.limelight.nvstream.http

import com.limelight.LimeLog

class NvApp() {
    var appName: String? = ""
    var appId: Int = 0
        set(value) {
            field = value
            isInitialized = true
        }

    var isHdrSupported: Boolean = false
    var isInitialized: Boolean = false
        private set

    constructor(appName: String?) : this() {
        this.appName = appName
    }

    constructor(appName: String?, appId: Int, hdrSupported: Boolean) : this() {
        this.appName = appName
        this.appId = appId
        this.isHdrSupported = hdrSupported
    }

    fun setAppId(appId: String?) {
        try {
            this.appId =
                Integer.parseInt(appId ?: throw NumberFormatException("Cannot parse null string"))
        } catch (e: NumberFormatException) {
            LimeLog.warning("Malformed app ID: $appId")
        }
    }

    override fun toString(): String =
        "Name: $appName\n" +
            "HDR Supported: ${if (isHdrSupported) "Yes" else "Unknown"}\n" +
            "ID: $appId\n"
}
