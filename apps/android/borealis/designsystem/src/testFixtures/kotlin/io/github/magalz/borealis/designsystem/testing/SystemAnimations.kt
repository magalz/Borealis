package io.github.magalz.borealis.designsystem.testing

import android.provider.Settings
import org.robolectric.RuntimeEnvironment

/**
 * Sets the animator duration scale of the system, which Reduce motion and the animated background
 * read: 0 removes the animations, 1 is the default. Robolectric only; the setting is global to the
 * test.
 */
fun systemAnimationScale(scale: Float) {
    Settings.Global.putFloat(
        RuntimeEnvironment.getApplication().contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        scale,
    )
}

/**
 * Takes the animation from the system, so that a host of a video style shows the still of its first
 * frame, which is the same at every run.
 */
fun noAnimations() = systemAnimationScale(0f)
