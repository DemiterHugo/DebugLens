package io.github.demiterhugo.debuglens.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.demiterhugo.debuglens.R
import io.github.demiterhugo.debuglens.domain.model.DiagnosticInput
import io.github.demiterhugo.debuglens.domain.model.DiagnosticInputError
import io.github.demiterhugo.debuglens.domain.model.DiagnosticSession
import io.github.demiterhugo.debuglens.domain.repository.LogcatImportResult
import io.github.demiterhugo.debuglens.ui.diagnostic.DiagnosticAiControls
import io.github.demiterhugo.debuglens.ui.diagnostic.DiagnosticAiViewModel
import io.github.demiterhugo.debuglens.ui.diagnostic.DiagnosticSurfaceState
import io.github.demiterhugo.debuglens.ui.diagnostic.DiagnosticSurfaceViewModel
import io.github.demiterhugo.debuglens.ui.diagnostic.diagnosticSurfaceCopy
import io.github.demiterhugo.debuglens.ui.diagnostic.titleResource
import io.github.demiterhugo.debuglens.ui.theme.DebugLensTheme
import kotlinx.coroutines.launch

@Composable
fun HomeRoute(modifier: Modifier = Modifier, viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val logcatPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importLogcat(uri.toString())
    }
    val surfaceViewModel: DiagnosticSurfaceViewModel = hiltViewModel()
    val aiViewModel: DiagnosticAiViewModel = hiltViewModel()
    val aiState by aiViewModel.state.collectAsStateWithLifecycle()
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) aiViewModel.installModel(uri.toString())
    }
    val surfaceState by surfaceViewModel.state.collectAsStateWithLifecycle()
    val session = uiState.selectedSession
    LaunchedEffect(session?.id) { aiViewModel.selectSession(session?.id) }
    val advice = aiState.advice.takeIf { aiState.sessionId == session?.id }
    val copy = session?.let { diagnosticSurfaceCopy(it.analysis, advice) }
    LaunchedEffect(session, copy) {
        if (session != null && copy != null) surfaceViewModel.show(session, copy) else surfaceViewModel.clear()
    }
    HomeScreen(
        uiState = uiState,
        onInputChange = viewModel::updateInput,
        onAnalyze = viewModel::analyze,
        onImportLogcat = { logcatPicker.launch(arrayOf("text/*", "application/octet-stream")) },
        onOpenSession = viewModel::openSession,
        onClearHistory = viewModel::clearHistory,
        onRetryHistory = viewModel::retryHistory,
        onShowAbout = viewModel::showAbout,
        onDismissAbout = viewModel::dismissAbout,
        surfaceState = surfaceState,
        onRetrySurface = surfaceViewModel::retry,
        aiControls = { selected ->
            DiagnosticAiControls(
                state = aiState,
                onInstall = { modelPicker.launch(arrayOf("*/*")) },
                onRemove = aiViewModel::removeModel,
                onGenerate = { aiViewModel.generate(selected) },
                onCancel = aiViewModel::cancelGeneration,
            )
        },
        modifier = modifier,
    )
}

@Composable
internal fun HomeScreen(
    uiState: HomeUiState,
    onInputChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    onImportLogcat: () -> Unit,
    onOpenSession: (String) -> Unit,
    onClearHistory: () -> Unit,
    onRetryHistory: () -> Unit,
    onShowAbout: () -> Unit,
    onDismissAbout: () -> Unit,
    modifier: Modifier = Modifier,
    surfaceState: DiagnosticSurfaceState = DiagnosticSurfaceState(),
    onRetrySurface: () -> Unit = {},
    aiControls: @Composable (DiagnosticSession) -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val resultRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val example = stringResource(R.string.example_stack_trace)
    LaunchedEffect(uiState.selectedSession?.id) {
        if (uiState.selectedSession != null) {
            withFrameNanos { }
            resultRequester.bringIntoView()
        }
    }
    Scaffold(modifier = modifier.fillMaxSize()) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onShowAbout) { Text(stringResource(R.string.home_info_button)) }
            }
            Text(
                stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.home_input_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { onInputChange(example) },
                    modifier = Modifier.testTag("load_example"),
                ) { Text(stringResource(R.string.action_load_example)) }
            }
            TextButton(
                onClick = {
                    keyboard?.hide()
                    focusManager.clearFocus()
                    onImportLogcat()
                },
                enabled = !uiState.isInputBusy && !uiState.isClearingHistory,
                modifier = Modifier.testTag("import_logcat"),
            ) {
                Text(stringResource(if (uiState.isImportingLogcat) R.string.action_importing_logcat else R.string.action_import_logcat))
            }
            uiState.logcatImportError?.let { error ->
                Text(
                    stringResource(
                        when (error) {
                            LogcatImportResult.Failure.UNREADABLE -> R.string.logcat_unreadable
                            LogcatImportResult.Failure.FILE_TOO_LARGE -> R.string.logcat_file_too_large
                            LogcatImportResult.Failure.INVALID_ENCODING -> R.string.logcat_invalid_encoding
                            LogcatImportResult.Failure.NO_INCIDENT -> R.string.logcat_no_incident
                            LogcatImportResult.Failure.INCIDENT_TOO_LONG -> R.string.logcat_incident_too_long
                        },
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedTextField(
                value = uiState.input,
                onValueChange = onInputChange,
                label = { Text(stringResource(R.string.home_input_label)) },
                placeholder = { Text(stringResource(R.string.home_input_placeholder)) },
                modifier = Modifier.fillMaxWidth().testTag("diagnostic_input"),
                minLines = 4,
                maxLines = 8,
                isError = uiState.inputError != null,
                supportingText = {
                    Text(
                        when (uiState.inputError) {
                            DiagnosticInputError.EMPTY -> stringResource(R.string.input_error_empty)
                            DiagnosticInputError.TOO_LONG -> stringResource(
                                R.string.input_error_too_long, DiagnosticInput.MAX_LENGTH,
                            )
                            null -> stringResource(
                                R.string.input_character_count, uiState.input.length, DiagnosticInput.MAX_LENGTH,
                            )
                        },
                    )
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        keyboard?.hide()
                        focusManager.clearFocus()
                        onAnalyze()
                    },
                    enabled = !uiState.isInputBusy && !uiState.isClearingHistory,
                    modifier = Modifier.testTag("analyze_input"),
                ) {
                    Text(stringResource(if (uiState.isAnalyzing) R.string.action_analyzing else R.string.action_analyze))
                }
                TextButton(
                    onClick = { onInputChange("") },
                    enabled = uiState.input.isNotEmpty(),
                    modifier = Modifier.testTag("clear_input"),
                ) { Text(stringResource(R.string.action_clear_input)) }
            }
            if (uiState.isInputBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            uiState.operationError?.let { error ->
                Text(
                    stringResource(
                        when (error) {
                            HomeOperationError.ANALYSIS -> R.string.analysis_error
                            HomeOperationError.HISTORY_SAVE -> R.string.history_save_error
                            HomeOperationError.HISTORY_CLEAR -> R.string.history_clear_error
                        },
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            uiState.selectedSession?.let {
                aiControls(it)
                DiagnosticResultCard(
                    sessionId = it.id,
                    surfaceState = surfaceState,
                    onRetry = onRetrySurface,
                    headerModifier = Modifier.bringIntoViewRequester(resultRequester).testTag("analysis_heading"),
                )
            }
            Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.history_retention),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (uiState.isHistoryLoading) {
                Text(stringResource(R.string.history_loading), modifier = Modifier.testTag("history_loading"))
            } else if (uiState.historyLoadError) {
                Text(stringResource(R.string.history_load_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetryHistory, modifier = Modifier.testTag("retry_history")) {
                    Text(stringResource(R.string.action_retry_history))
                }
            } else if (uiState.recentSessions.isEmpty()) {
                Text(stringResource(R.string.history_empty), modifier = Modifier.testTag("history_empty"))
            } else {
                TextButton(
                    onClick = onClearHistory,
                    enabled = !uiState.isInputBusy && !uiState.isClearingHistory,
                    modifier = Modifier.testTag("clear_history"),
                ) {
                    Text(stringResource(if (uiState.isClearingHistory) R.string.action_clearing_history else R.string.action_clear_history))
                }
                uiState.recentSessions.forEach { session ->
                    Card(
                        onClick = {
                            val alreadyDisplayed = uiState.selectedSession?.id == session.id
                            onOpenSession(session.id)
                            if (alreadyDisplayed) scope.launch { resultRequester.bringIntoView() }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("history_entry"),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                stringResource(session.analysis.category.titleResource),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                session.analysis.rootCause?.message ?: session.analysis.rootCause?.type
                                    ?: session.analysis.anrEvidence ?: session.analysis.input.text.lineSequence().first(),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
    if (uiState.isAboutVisible) {
        AlertDialog(
            onDismissRequest = onDismissAbout,
            title = { Text(stringResource(R.string.home_about)) },
            text = { Text(stringResource(R.string.home_about_description)) },
            confirmButton = {
                TextButton(onClick = onDismissAbout) { Text(stringResource(R.string.action_close)) }
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    DebugLensTheme(dynamicColor = false) {
        HomeScreen(
            uiState = HomeUiState(),
            onInputChange = {},
            onAnalyze = {},
            onImportLogcat = {},
            onOpenSession = {},
            onClearHistory = {},
            onRetryHistory = {},
            onShowAbout = {},
            onDismissAbout = {},
        )
    }
}
