package io.github.magalz.borealis.host.video

/** Stub, replaced by the implementation commit. */
class FirstFrameSignal {
    @Volatile var listener: Runnable? = null

    fun frameRendered() = Unit
}
