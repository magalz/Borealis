package io.github.magalz.borealis.library

import com.limelight.nvstream.http.ComputerDetails

/**
 * An exact copy of these details that shares nothing mutable with them, address tuples included:
 * the service goes on mutating its own objects in place.
 */
internal fun ComputerDetails.snapshot(): ComputerDetails = TODO()
