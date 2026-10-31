package io.github.demiterhugo.debuglens.ui.diagnostic

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.demiterhugo.debuglens.R
import io.github.demiterhugo.debuglens.domain.model.DiagnosticCategory
import io.github.demiterhugo.debuglens.domain.model.DiagnosticAnalysis

@Composable
internal fun diagnosticSurfaceCopy(analysis: DiagnosticAnalysis): DiagnosticSurfaceCopy {
    val category = analysis.category
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
    return DiagnosticSurfaceCopy(
        label = stringResource(R.string.analysis_title),
        title = stringResource(category.titleResource),
        limitations = stringResource(R.string.analysis_limitations),
        location = stringResource(R.string.analysis_location),
        guidanceTitle = stringResource(R.string.analysis_guidance),
        guidance = stringResource(category.guidanceResource),
        checklistTitle = stringResource(R.string.a2ui_checklist_title),
        checklist = listOf(
            stringResource(firstCheck),
            stringResource(R.string.check_message_and_trace),
            stringResource(R.string.check_reproduce),
        ),
        showCauses = stringResource(R.string.a2ui_show_causes),
        hideCauses = stringResource(R.string.a2ui_hide_causes),
        causesTitle = stringResource(R.string.analysis_cause_count, analysis.causes.size),
        causesOmitted = stringResource(R.string.a2ui_causes_omitted, DiagnosticSurfaceFactory.MAX_VISIBLE_CAUSES),
        showSource = stringResource(R.string.a2ui_show_source),
        hideSource = stringResource(R.string.a2ui_hide_source),
        sourceOmitted = stringResource(R.string.a2ui_source_omitted),
    )
}
