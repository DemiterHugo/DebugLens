package io.github.demiterhugo.debuglens.ui.diagnostic

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.demiterhugo.debuglens.R
import io.github.demiterhugo.debuglens.domain.model.DiagnosticCategory

internal data class DiagnosticPresentationText(
    val label: String,
    val title: String,
    val limitations: String,
    val location: String,
    val guidanceTitle: String,
    val guidance: String,
    val checklistTitle: String,
    val checklist: List<String>,
    val ai: AiDiagnosticCopy? = null,
)

internal data class AiDiagnosticCopy(val title: String, val advice: String, val note: String)

@Composable
internal fun diagnosticText(category: DiagnosticCategory, aiAdvice: String? = null): DiagnosticPresentationText {
    val firstCheck = when (category) {
        DiagnosticCategory.NULL_REFERENCE -> R.string.check_null_reference
        DiagnosticCategory.INVALID_STATE -> R.string.check_invalid_state
        DiagnosticCategory.INVALID_ARGUMENT -> R.string.check_invalid_argument
        DiagnosticCategory.INDEX_OUT_OF_BOUNDS -> R.string.check_index_out_of_bounds
        DiagnosticCategory.TYPE_MISMATCH -> R.string.check_type_mismatch
        DiagnosticCategory.SECURITY -> R.string.check_security
        DiagnosticCategory.OUT_OF_MEMORY -> R.string.check_out_of_memory
        DiagnosticCategory.UNRESPONSIVE -> R.string.check_unresponsive
        DiagnosticCategory.UNKNOWN -> R.string.check_unknown
    }
    return DiagnosticPresentationText(
        label = stringResource(R.string.analysis_title),
        title = stringResource(category.titleResource),
        limitations = stringResource(R.string.analysis_limitations),
        location = stringResource(R.string.analysis_location),
        guidanceTitle = stringResource(R.string.analysis_guidance),
        guidance = stringResource(category.guidanceResource),
        checklistTitle = stringResource(R.string.analysis_checklist_title),
        checklist = listOf(
            stringResource(firstCheck),
            stringResource(R.string.check_message_and_trace),
            stringResource(R.string.check_reproduce),
        ),
        ai = aiAdvice?.let {
            AiDiagnosticCopy(
                title = stringResource(R.string.ai_result_title),
                advice = it,
                note = stringResource(R.string.ai_result_note),
            )
        },
    )
}
