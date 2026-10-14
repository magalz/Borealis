package io.github.magalz.borealis.host.video

import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.IntConsumer

/** Stub, replaced by the implementation commit. */
object StreamStartup {
    @JvmStatic
    @JvmOverloads
    @Suppress("LongParameterList", "UNUSED_PARAMETER")
    fun create(
        runOnUi: Consumer<Runnable>,
        setOverlayText: IntConsumer,
        setRequestedVisibility: IntConsumer,
        applyVisibility: IntConsumer,
        isHidingOverlays: BooleanSupplier,
        terminateNoVideoFrame: Runnable,
        scheduler: WatchdogScheduler = HandlerWatchdogScheduler(),
    ): StreamStartupGuard =
        StreamStartupGuard(
            object : StreamStartupHost {
                override fun showPreparing() = Unit

                override fun clearPreparing() = Unit

                override fun runOnUi(action: Runnable) = Unit

                override fun terminateNoVideoFrame() = Unit
            },
            scheduler,
        )
}
