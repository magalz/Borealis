package io.github.magalz.borealis.library

import javax.inject.Qualifier

/** The dispatcher for blocking network and service calls. */
@Qualifier @Retention(AnnotationRetention.BINARY) internal annotation class IoDispatcher
