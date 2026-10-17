package io.github.magalz.borealis.settings.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.magalz.borealis.settings.device.DeviceFactsSource
import io.github.magalz.borealis.settings.device.OscLayoutReset
import io.github.magalz.borealis.settings.options.SettingsOptions
import io.github.magalz.borealis.settings.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The state and the actions of the settings screen. */
@HiltViewModel
class SettingsViewModel
internal constructor(
    private val repository: SettingsRepository,
    private val options: SettingsOptions,
    private val deviceFacts: DeviceFactsSource,
    private val oscLayoutReset: OscLayoutReset,
    private val io: CoroutineDispatcher,
) : ViewModel() {
    @Inject
    constructor(
        repository: SettingsRepository,
        options: SettingsOptions,
        deviceFacts: DeviceFactsSource,
        oscLayoutReset: OscLayoutReset,
    ) : this(repository, options, deviceFacts, oscLayoutReset, Dispatchers.IO)

    /** The state of the screen. */
    val uiState: StateFlow<SettingsUiState> =
        MutableStateFlow(
            SettingsUiState(
                repository.snapshot.value,
                emptyList(),
                emptyList(),
                emptyList(),
                0,
                true,
            )
        )

    /** Stores [checked] for the switch [item]. */
    fun onSwitchChanged(item: SettingsItem, checked: Boolean) = Unit

    /** Stores the entry [key] of the list [item]. */
    fun onChoiceSelected(item: SettingsItem, key: String) = Unit

    /** Stores [value], in the stored unit, for the slider [item]. */
    fun onSliderChanged(item: SettingsItem, value: Int) = Unit

    /** Resets the layout of the on-screen controls. The screen asks before it calls this. */
    fun onResetLayout() = Unit

    /** Asks the device for its facts again, because the display may have changed. */
    fun refreshDeviceFacts() = Unit
}
