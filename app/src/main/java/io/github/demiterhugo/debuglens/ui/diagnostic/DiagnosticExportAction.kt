package io.github.demiterhugo.debuglens.ui.diagnostic

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

@Composable
internal fun DiagnosticExportAction(
    sessionId: String,
    hasAdvice: Boolean,
    status: DiagnosticExportStatus,
    onExport: (includeTrace: Boolean, includeAdvice: Boolean) -> Unit,
) {
    var showOptions by rememberSaveable(sessionId) { mutableStateOf(false) }
    TextButton(
        onClick = { showOptions = true },
        enabled = !status.isBusy,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).testTag("export_diagnostic"),
    ) {
        val label = if (status == DiagnosticExportStatus.WRITING) R.string.export_writing else R.string.export_title
        Text(stringResource(label))
    }
    if (!showOptions) return

    var includeTrace by rememberSaveable(sessionId) { mutableStateOf(false) }
    var includeAdvice by rememberSaveable(sessionId) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { showOptions = false },
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
            TextButton(onClick = {
                showOptions = false
                onExport(includeTrace, hasAdvice && includeAdvice)
            }) {
                Text(stringResource(R.string.export_choose_destination))
            }
        },
        dismissButton = {
            TextButton(onClick = { showOptions = false }) { Text(stringResource(R.string.export_cancel)) }
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
