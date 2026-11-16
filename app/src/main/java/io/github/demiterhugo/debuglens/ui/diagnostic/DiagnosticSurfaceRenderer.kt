package io.github.demiterhugo.debuglens.ui.diagnostic

import androidx.a2ui.compose.runtime.A2uiComponentState
import androidx.a2ui.compose.runtime.LocalA2uiReadinessEvaluator
import androidx.a2ui.compose.runtime.observeA2uiComponentState
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.a2ui.compose.ui.asReadinessEvaluator
import androidx.a2ui.model.processor.A2uiSurfaceModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.demiterhugo.debuglens.R

@Composable
internal fun DiagnosticSurfaceRenderer(
    surface: A2uiSurfaceModel,
    onRetry: () -> Unit,
    headerModifier: Modifier,
    frame: @Composable (content: @Composable () -> Unit) -> Unit,
) {
    val evaluator = remember { DiagnosticCatalog.asReadinessEvaluator() }
    CompositionLocalProvider(
        LocalA2uiReadinessEvaluator provides evaluator,
        LocalDiagnosticHeadingModifier provides headerModifier,
    ) {
        when (val state = observeA2uiComponentState(surface)) {
            A2uiComponentState.Loading -> Unit
            is A2uiComponentState.Error -> frame { DiagnosticSurfaceError(onRetry, headerModifier) }
            is A2uiComponentState.Success -> frame {
                Box(Modifier.testTag("a2ui_surface_ready")) {
                    A2uiComponent(state.component)
                }
            }
        }
    }
}

@Composable
internal fun DiagnosticSurfaceError(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.testTag("a2ui_surface_error")) {
        Text(
            stringResource(R.string.a2ui_surface_error),
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onRetry) { Text(stringResource(R.string.a2ui_retry)) }
    }
}
