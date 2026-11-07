package io.github.demiterhugo.debuglens.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.demiterhugo.debuglens.ui.diagnostic.DiagnosticSurfaceError
import io.github.demiterhugo.debuglens.ui.diagnostic.DiagnosticSurfaceLoading
import io.github.demiterhugo.debuglens.ui.diagnostic.DiagnosticSurfaceRenderer
import io.github.demiterhugo.debuglens.ui.diagnostic.DiagnosticSurfaceState

@Composable
internal fun DiagnosticResultCard(
    sessionId: String,
    surfaceState: DiagnosticSurfaceState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    headerModifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Card(modifier = modifier.fillMaxWidth().testTag("analysis_result")) {
        actions()
        Box(Modifier.padding(20.dp)) {
            when {
                surfaceState.sessionId != sessionId -> DiagnosticSurfaceLoading(headerModifier)
                surfaceState.hasError -> DiagnosticSurfaceError(onRetry, headerModifier)
                surfaceState.surface != null -> DiagnosticSurfaceRenderer(
                    surfaceState.surface, onRetry, headerModifier = headerModifier,
                )
                else -> DiagnosticSurfaceLoading(headerModifier)
            }
        }
    }
}
