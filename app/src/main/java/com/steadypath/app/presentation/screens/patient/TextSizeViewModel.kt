package com.steadypath.app.presentation.screens.patient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.model.TextScale
import com.steadypath.app.domain.repository.TextScalePreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TextSizeViewModel @Inject constructor(
    private val repository: TextScalePreferencesRepository
) : ViewModel() {

    val textScale: StateFlow<TextScale> = repository.textScale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TextScale.NORMAL)

    fun onTextScaleSelected(value: TextScale) {
        viewModelScope.launch { repository.setTextScale(value) }
    }
}
