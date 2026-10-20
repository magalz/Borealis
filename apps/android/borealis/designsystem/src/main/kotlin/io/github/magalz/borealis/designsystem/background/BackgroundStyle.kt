package io.github.magalz.borealis.designsystem.background

import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import io.github.magalz.borealis.designsystem.R

/**
 * The looks of the animated background behind the Library and Settings screens (issue #30). Two are
 * drawn in Compose, four play a clip, and [Off] is the plain page colour. [label] is the working
 * name of the style.
 */
enum class BackgroundStyle(@StringRes val label: Int, internal val clip: BackgroundClip? = null) {
    Off(R.string.borealis_background_off),
    AuroraCurtains(R.string.borealis_background_aurora_curtains),
    SoftGlow(R.string.borealis_background_soft_glow),
    NorthernLights(
        R.string.borealis_background_northern_lights,
        BackgroundClip(
            R.raw.background_northern_lights,
            R.drawable.background_northern_lights_still,
        ),
    ),
    LightMist(
        R.string.borealis_background_light_mist,
        BackgroundClip(R.raw.background_light_mist, R.drawable.background_light_mist_still),
    ),
    FlowingLight(
        R.string.borealis_background_flowing_light,
        BackgroundClip(R.raw.background_flowing_light, R.drawable.background_flowing_light_still),
    ),
    Starfield(
        R.string.borealis_background_starfield,
        BackgroundClip(R.raw.background_starfield, R.drawable.background_starfield_still),
    );

    companion object {
        /** What the Library shows until the user chooses another. */
        val Default = AuroraCurtains
    }
}

/** A video style's clip, and the still of its first frame that shows where no decoder runs. */
internal class BackgroundClip(@RawRes val video: Int, @DrawableRes val still: Int)
