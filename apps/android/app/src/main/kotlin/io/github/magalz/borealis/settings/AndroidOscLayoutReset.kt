package io.github.magalz.borealis.settings

import android.content.Context
import androidx.core.content.edit
import com.limelight.binding.input.virtual_controller.VirtualControllerConfigurationLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.magalz.borealis.settings.device.OscLayoutReset
import javax.inject.Inject

/** The [OscLayoutReset] of the app: it clears the file that holds the saved layout. */
internal class AndroidOscLayoutReset
@Inject
constructor(@ApplicationContext private val context: Context) : OscLayoutReset {
    override fun reset() {
        context
            .getSharedPreferences(
                VirtualControllerConfigurationLoader.OSC_PREFERENCE,
                Context.MODE_PRIVATE,
            )
            .edit { clear() }
    }
}
