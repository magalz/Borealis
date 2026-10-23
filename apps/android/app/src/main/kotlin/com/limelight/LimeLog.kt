package com.limelight

import java.util.logging.Logger

object LimeLog {
    private val logger = Logger.getLogger(LimeLog::class.java.name)

    @JvmStatic fun info(msg: String?) = logger.info(msg)

    @JvmStatic fun warning(msg: String?) = logger.warning(msg)

    @JvmStatic fun severe(msg: String?) = logger.severe(msg)
}
