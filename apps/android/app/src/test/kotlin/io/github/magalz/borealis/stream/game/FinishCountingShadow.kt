package io.github.magalz.borealis.stream.game

import android.app.Activity
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowActivity

/**
 * The shadow of `Activity` that Robolectric has, counting the calls to `finish`. The real activity
 * asks the system twice when a second call comes, and the system logs "Duplicate finish request";
 * Robolectric's shadow only sets the flag, so the count is how a test sees the second request.
 *
 * Use it with `@Config(shadows = [FinishCountingShadow::class, ...])` and read it with
 * [finishRequests].
 */
@Implements(Activity::class)
internal class FinishCountingShadow : ShadowActivity() {
    var requests = 0
        private set

    @Implementation
    override fun finish() {
        requests++
        super.finish()
    }
}

/** How many times `finish` was called on this activity. */
internal val Activity.finishRequests: Int
    get() = Shadow.extract<FinishCountingShadow>(this).requests
