package com.steadypath.app.presentation.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.usecase.RestoreSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val restoreSessionUseCase: RestoreSessionUseCase
) : ViewModel() {

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>(replay = 1)
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            val user = restoreSessionUseCase()
            _navigationEvent.emit(
                if (user != null) NavigationEvent.SessionRestored(user.rol)
                else NavigationEvent.NoSession
            )
        }
    }

    sealed class NavigationEvent {
        data class SessionRestored(val rol: String) : NavigationEvent()
        data object NoSession : NavigationEvent()
    }
}
