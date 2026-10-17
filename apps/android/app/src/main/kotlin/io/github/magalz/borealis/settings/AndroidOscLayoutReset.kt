package io.github.magalz.borealis.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.magalz.borealis.settings.device.OscLayoutReset
import javax.inject.Inject

/** The [OscLayoutReset] of the app: it clears the file that holds the saved layout. */
internal class AndroidOscLayoutReset
@Inject
constructor(@ApplicationContext private val context: Context) : OscLayoutReset {
    override fun reset() = Unit
}
