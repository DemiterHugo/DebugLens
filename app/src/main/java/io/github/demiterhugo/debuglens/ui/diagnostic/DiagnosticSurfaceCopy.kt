package io.github.demiterhugo.debuglens.ui.diagnostic

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.demiterhugo.debuglens.R
import io.github.demiterhugo.debuglens.domain.model.DiagnosticAnalysis

@Composable
internal fun diagnosticSurfaceCopy(
    analysis: DiagnosticAnalysis,
    text: DiagnosticPresentationText = diagnosticText(analysis.category),
): DiagnosticSurfaceCopy = DiagnosticSurfaceCopy(
    text = text,
    showCauses = stringResource(R.string.a2ui_show_causes),
    hideCauses = stringResource(R.string.a2ui_hide_causes),
    causesOmitted = stringResource(R.string.a2ui_causes_omitted, DiagnosticSurfaceFactory.MAX_VISIBLE_CAUSES),
    showSource = stringResource(R.string.a2ui_show_source),
    hideSource = stringResource(R.string.a2ui_hide_source),
    sourceOmitted = stringResource(R.string.a2ui_source_omitted),
)
