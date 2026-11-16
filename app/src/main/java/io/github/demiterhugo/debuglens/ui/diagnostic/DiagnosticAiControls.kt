package io.github.demiterhugo.debuglens.ui.diagnostic

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.demiterhugo.debuglens.R
import io.github.demiterhugo.debuglens.domain.repository.AiModelInstallResult
import io.github.demiterhugo.debuglens.domain.repository.AiModelStatus
import io.github.demiterhugo.debuglens.domain.repository.DiagnosticAiResult

@Composable
internal fun DiagnosticAiControls(
    state: DiagnosticAiUiState,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
    onGenerate: () -> Unit,
    onCancel: () -> Unit,
) {
    val model = state.model ?: return
    Column {
        if (model == AiModelStatus.UNSUPPORTED) {
            Text(stringResource(R.string.ai_unsupported), style = MaterialTheme.typography.bodySmall)
            return@Column
        }
        if (model != AiModelStatus.READY) {
            Text(stringResource(R.string.ai_model_required), style = MaterialTheme.typography.bodySmall)
        }
        if (state.generating) {
            Text(stringResource(R.string.ai_generating))
            TextButton(onClick = onCancel) { Text(stringResource(R.string.ai_cancel)) }
        } else if (model == AiModelStatus.READY) {
            Button(onClick = onGenerate, enabled = !state.changingModel, modifier = Modifier.testTag("generate_ai")) {
                Text(stringResource(R.string.ai_generate))
            }
        }
        if (state.changingModel) Text(stringResource(R.string.ai_changing_model))
        FlowRow {
            TextButton(
                onClick = onInstall,
                enabled = !state.changingModel && !state.generating,
                modifier = Modifier.testTag("choose_ai_model"),
            ) {
                Text(stringResource(if (model == AiModelStatus.READY) R.string.ai_replace_model else R.string.ai_load_model))
            }
            if (model == AiModelStatus.READY) {
                TextButton(onClick = onRemove, enabled = !state.changingModel && !state.generating) {
                    Text(stringResource(R.string.ai_remove_model))
                }
            }
        }
        val error = when (state.modelError) {
            AiModelInstallResult.INVALID_FILE -> R.string.ai_invalid_model
            AiModelInstallResult.TOO_LARGE -> R.string.ai_model_too_large
            AiModelInstallResult.UNREADABLE -> R.string.ai_model_error
            AiModelInstallResult.INSTALLED, null -> when (state.generationError) {
                DiagnosticAiResult.Failure.UNAVAILABLE -> R.string.ai_unavailable
                DiagnosticAiResult.Failure.GENERATION_FAILED -> R.string.ai_generation_error
                null -> null
            }
        }
        error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
    }
}
