package io.github.magalz.borealis.library.apps

import androidx.lifecycle.ViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.magalz.borealis.library.domain.AppRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

@HiltViewModel(assistedFactory = AppsViewModel.Factory::class)
class AppsViewModel
@AssistedInject
constructor(@Assisted private val hostId: String, private val repository: AppRepository) :
    ViewModel() {
    val uiState: StateFlow<AppsUiState> = MutableStateFlow(AppsUiState())
    val effects: Flow<AppsEffect> = emptyFlow()

    fun onAppSelected(appId: Int) = Unit

    fun onActions(appId: Int) = Unit

    fun onActionChosen(action: AppAction) = Unit

    fun onActionsDismissed() = Unit

    fun onDetailsDismissed() = Unit

    fun onToggleShowHidden() = Unit

    @AssistedFactory
    interface Factory {
        fun create(hostId: String): AppsViewModel
    }
}
