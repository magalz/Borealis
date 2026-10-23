package com.limelight.computers

import com.limelight.nvstream.http.ComputerDetails

fun interface ComputerManagerListener {
    fun notifyComputerUpdated(details: ComputerDetails)
}
