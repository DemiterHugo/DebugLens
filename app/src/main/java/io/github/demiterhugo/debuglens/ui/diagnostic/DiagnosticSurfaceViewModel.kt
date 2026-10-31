package io.github.demiterhugo.debuglens.ui.diagnostic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.demiterhugo.debuglens.di.AnalysisDispatcher
import io.github.demiterhugo.debuglens.domain.model.DiagnosticSession
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher

@HiltViewModel
internal class DiagnosticSurfaceViewModel @Inject constructor(
    @AnalysisDispatcher dispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val controller = DiagnosticSurfaceController(viewModelScope, dispatcher)
    val state = controller.state

    fun show(session: DiagnosticSession, copy: DiagnosticSurfaceCopy) = controller.show(session, copy)
    fun clear() = controller.clear()
    fun retry() = controller.retry()
}
