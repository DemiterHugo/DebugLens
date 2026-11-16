package io.github.demiterhugo.debuglens.ui.diagnostic

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.demiterhugo.debuglens.R

internal enum class DiagnosticReportDestination { FOLDER, SHARE_TEXT }

@Composable
internal fun DiagnosticExportAction(
    sessionId: String,
    hasAdvice: Boolean,
    status: DiagnosticExportStatus,
    onConfirm: (destination: DiagnosticReportDestination, includeTrace: Boolean, includeAdvice: Boolean) -> Unit,
) = key(sessionId) {
    var showOptions by rememberSaveable { mutableStateOf(false) }
    FilledTonalButton(
        onClick = { showOptions = true },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("export_diagnostic"),
    ) {
        Text(stringResource(R.string.export_action))
    }
    if (showOptions) {
        DiagnosticReportOptions(
            hasAdvice = hasAdvice,
            canSaveFile = !status.isBusy,
            onDismiss = { showOptions = false },
            onConfirm = onConfirm,
        )
    }
}

@Composable
private fun DiagnosticReportOptions(
    hasAdvice: Boolean,
    canSaveFile: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (DiagnosticReportDestination, Boolean, Boolean) -> Unit,
) {
    var includeTrace by rememberSaveable { mutableStateOf(false) }
    var includeAdvice by rememberSaveable { mutableStateOf(false) }
    fun confirm(destination: DiagnosticReportDestination) {
        onDismiss()
        onConfirm(destination, includeTrace, hasAdvice && includeAdvice)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_title)) },
        text = {
            Column {
                Text(stringResource(R.string.export_options_description))
                ExportOption(
                    label = stringResource(R.string.export_include_trace),
                    checked = includeTrace,
                    onCheckedChange = { includeTrace = it },
                    modifier = Modifier.testTag("export_include_trace"),
                )
                if (hasAdvice) {
                    ExportOption(
                        label = stringResource(R.string.export_include_advice),
                        checked = includeAdvice,
                        onCheckedChange = { includeAdvice = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { confirm(DiagnosticReportDestination.SHARE_TEXT) },
                modifier = Modifier.testTag("share_diagnostic_text"),
            ) {
                Text(stringResource(R.string.export_share_text))
            }
            TextButton(
                onClick = { confirm(DiagnosticReportDestination.FOLDER) },
                enabled = canSaveFile,
                modifier = Modifier.testTag("export_choose_folder"),
            ) {
                Text(stringResource(R.string.export_choose_destination))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.export_cancel)) }
        },
    )
}

@Composable
private fun ExportOption(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .toggleable(checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, Modifier.padding(start = 12.dp))
    }
}
