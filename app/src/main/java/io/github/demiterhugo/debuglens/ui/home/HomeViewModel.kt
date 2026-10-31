package io.github.demiterhugo.debuglens.ui.home

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    fun showAbout() {
        _uiState.update { it.copy(isAboutVisible = true) }
    }

    fun dismissAbout() {
        _uiState.update { it.copy(isAboutVisible = false) }
    }
}
