package io.github.magalz.borealis.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.magalz.borealis.settings.catalog.BooleanSetting
import io.github.magalz.borealis.settings.catalog.IntSetting
import io.github.magalz.borealis.settings.catalog.OptionalIntSetting
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.catalog.StringSetting
import io.github.magalz.borealis.settings.device.DeviceFactsSource
import io.github.magalz.borealis.settings.device.OscLayoutReset
import io.github.magalz.borealis.settings.options.SettingsOptions
import io.github.magalz.borealis.settings.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The state and the actions of the settings screen (UC-009). Every change is stored at once through
 * the [SettingsRepository]; the rows that the device cannot use are left out, and the rows that
 * another setting blocks cannot be changed.
 *
 * The device facts are asked when the ViewModel is created and again on [refreshDeviceFacts],
 * because the display can change.
 */
@HiltViewModel
class SettingsViewModel
internal constructor(
    private val repository: SettingsRepository,
    options: SettingsOptions,
    private val deviceFactsSource: DeviceFactsSource,
    private val oscLayoutReset: OscLayoutReset,
    private val io: CoroutineDispatcher,
) : ViewModel() {
    @Inject
    constructor(
        repository: SettingsRepository,
        options: SettingsOptions,
        deviceFactsSource: DeviceFactsSource,
        oscLayoutReset: OscLayoutReset,
    ) : this(repository, options, deviceFactsSource, oscLayoutReset, Dispatchers.IO)

    private val stateBuilder = SettingsStateBuilder(options)
    private val facts = MutableStateFlow(deviceFactsSource.facts())

    /** The state of the screen. */
    val uiState: StateFlow<SettingsUiState> =
        combine(repository.snapshot, facts, stateBuilder::build)
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                stateBuilder.build(repository.snapshot.value, facts.value),
            )

    init {
        viewModelScope.launch { restoreOfferedChoices() }
    }

    /** Stores [checked] for the switch [item]. */
    fun onSwitchChanged(item: SettingsItem, checked: Boolean) {
        val setting = item.setting as? BooleanSetting
        if (setting == null || !isChangeable(item)) return
        viewModelScope.launch {
            repository.set(setting, checked)
            // Turning Unlock all frame rates off can leave 90 or 120 fps stored but no longer
            // offered.
            if (item == SettingsItem.UNLOCK_FRAME_RATES) restoreOfferedChoices()
        }
    }

    /**
     * Stores the entry [key] of the list [item]. Changing the resolution or the frame rate also
     * returns the bitrate to automatic (UC-009, A4). A key that the list does not offer is ignored.
     */
    fun onChoiceSelected(item: SettingsItem, key: String) {
        if (!isChangeable(item) || key !in offeredKeys(item)) return
        viewModelScope.launch {
            when (val setting = item.setting) {
                Settings.TOUCHSCREEN_TRACKPAD ->
                    repository.set(
                        Settings.TOUCHSCREEN_TRACKPAD,
                        key == FixedChoices.TOUCH_TRACKPAD,
                    )
                Settings.RESOLUTION,
                Settings.FRAME_RATE -> {
                    repository.set(setting as StringSetting, key)
                    repository.clear(Settings.BITRATE_KBPS)
                }
                is StringSetting -> repository.set(setting, key)
                else -> Unit
            }
        }
    }

    /**
     * Stores [value], in the stored unit, for the slider [item]. A value outside the range of the
     * slider is kept in it.
     */
    fun onSliderChanged(item: SettingsItem, value: Int) {
        val spec = item.input.slider
        if (spec == null || !isChangeable(item)) return
        val inRange = value.coerceIn(spec.min, spec.max)
        viewModelScope.launch {
            when (val setting = item.setting) {
                is OptionalIntSetting -> repository.set(setting, inRange)
                is IntSetting -> repository.set(setting, inRange)
                else -> Unit
            }
        }
    }

    /**
     * Resets the layout of the on-screen controls, off the main thread. The screen asks before it
     * calls this.
     */
    fun onResetLayout() {
        if (!isChangeable(SettingsItem.RESET_LAYOUT)) return
        viewModelScope.launch { withContext(io) { oscLayoutReset.reset() } }
    }

    /** Asks the device for its facts again, because the display may have changed. */
    fun refreshDeviceFacts() {
        facts.value = deviceFactsSource.facts()
        viewModelScope.launch { restoreOfferedChoices() }
    }

    private fun isChangeable(item: SettingsItem): Boolean =
        uiState.value.groups.any { group ->
            group.items.any { it.item == item && it.disabled == null }
        }

    private fun offeredKeys(item: SettingsItem): List<String> =
        when (item) {
            SettingsItem.RESOLUTION -> uiState.value.resolutionChoices.map { it.key }
            SettingsItem.FRAME_RATE -> uiState.value.frameRateChoices.map { it.fps.toString() }
            else -> item.input.fixedChoices.map { it.key }
        }

    /**
     * Replaces a stored resolution or frame rate that this device does not offer by the nearest
     * lower one, and returns the bitrate to automatic, as the inherited screen did.
     */
    private suspend fun restoreOfferedChoices() {
        val snapshot = repository.snapshot.value
        val state = stateBuilder.build(snapshot, facts.value)
        val resolution =
            nearestLowerResolution(snapshot[Settings.RESOLUTION], state.resolutionChoices)
        val frameRate = nearestLowerFrameRate(snapshot[Settings.FRAME_RATE], state.frameRateChoices)
        val resolutionChanged = resolution != snapshot[Settings.RESOLUTION]
        val frameRateChanged = frameRate != snapshot[Settings.FRAME_RATE]
        if (resolutionChanged) repository.set(Settings.RESOLUTION, resolution)
        if (frameRateChanged) repository.set(Settings.FRAME_RATE, frameRate)
        if (resolutionChanged || frameRateChanged) repository.clear(Settings.BITRATE_KBPS)
    }
}
