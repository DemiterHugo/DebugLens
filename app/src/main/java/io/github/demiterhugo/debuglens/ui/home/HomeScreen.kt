package io.github.demiterhugo.debuglens.ui.home

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.demiterhugo.debuglens.R
import io.github.demiterhugo.debuglens.domain.model.DiagnosticCategory
import io.github.demiterhugo.debuglens.domain.model.DiagnosticInput
import io.github.demiterhugo.debuglens.domain.model.DiagnosticInputError
import io.github.demiterhugo.debuglens.domain.model.DiagnosticSession
import io.github.demiterhugo.debuglens.domain.repository.LogcatIncidentResult
import io.github.demiterhugo.debuglens.ui.diagnostic.DiagnosticAiControls
import io.github.demiterhugo.debuglens.ui.diagnostic.DiagnosticAiViewModel
import io.github.demiterhugo.debuglens.ui.diagnostic.DiagnosticExportAction
import io.github.demiterhugo.debuglens.ui.diagnostic.DiagnosticExportStatus
import io.github.demiterhugo.debuglens.ui.diagnostic.DiagnosticExportViewModel
import io.github.demiterhugo.debuglens.ui.diagnostic.DiagnosticReportDestination
import io.github.demiterhugo.debuglens.ui.diagnostic.DiagnosticReportLabels
import io.github.demiterhugo.debuglens.ui.diagnostic.DiagnosticSurfaceState
import io.github.demiterhugo.debuglens.ui.diagnostic.DiagnosticSurfaceViewModel
import io.github.demiterhugo.debuglens.ui.diagnostic.diagnosticSurfaceCopy
import io.github.demiterhugo.debuglens.ui.diagnostic.diagnosticText
import io.github.demiterhugo.debuglens.ui.diagnostic.formatDiagnosticReport
import io.github.demiterhugo.debuglens.ui.diagnostic.titleResource
import io.github.demiterhugo.debuglens.ui.theme.DebugLensTheme
import kotlinx.coroutines.delay
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
    val exportViewModel: DiagnosticExportViewModel = hiltViewModel()
    val exportStatus by exportViewModel.status.collectAsStateWithLifecycle()
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        exportViewModel.onDestinationSelected(uri?.toString())
    }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportProgress = stringResource(R.string.export_writing)
    val cancelExport = stringResource(R.string.export_cancel)
    val shareUnavailable = stringResource(R.string.export_share_unavailable)
    val exportMessage = when (exportStatus) {
        DiagnosticExportStatus.SAVED -> stringResource(R.string.export_saved)
        DiagnosticExportStatus.CANCELLED -> stringResource(R.string.export_cancelled)
        DiagnosticExportStatus.FAILED -> stringResource(R.string.export_failed)
        else -> null
    }
    LaunchedEffect(exportStatus, exportMessage, exportProgress, cancelExport) {
        if (exportStatus == DiagnosticExportStatus.WRITING) {
            snackbar.showSnackbar(exportProgress, cancelExport, duration = SnackbarDuration.Indefinite)
            exportViewModel.cancelWrite()
        } else if (exportMessage != null) {
            snackbar.showSnackbar(exportMessage)
            exportViewModel.dismissResult()
        }
    }
    val surfaceState by surfaceViewModel.state.collectAsStateWithLifecycle()
    val session = uiState.selectedSession
    LaunchedEffect(session?.id) { aiViewModel.selectSession(session?.id) }
    val advice = aiState.advice.takeIf { aiState.sessionId == session?.id }
    val text = session?.let { diagnosticText(it.analysis.category, advice) }
    val copy = if (session != null && text != null) diagnosticSurfaceCopy(session.analysis, text) else null
    LaunchedEffect(uiState.surfaceRevision, session, copy) {
        if (session != null && copy != null) {
            surfaceViewModel.show(session, copy, uiState.surfaceRevision)
        } else {
            surfaceViewModel.clear(uiState.surfaceRevision)
        }
    }
    HomeScreen(
        uiState = uiState,
        onInputChange = viewModel::updateInput,
        onAnalyze = viewModel::analyze,
        onImportLogcat = { logcatPicker.launch(arrayOf("text/*", "application/octet-stream")) },
        onCaptureLogcat = viewModel::captureLogcat,
        onCancelLogcatCapture = viewModel::cancelLogcatCapture,
        onOpenSession = { viewModel.openSession(it) },
        onDeleteSession = viewModel::deleteHistorySession,
        onHistoryQueryChange = viewModel::updateHistoryQuery,
        onHistoryCategorySelected = viewModel::selectHistoryCategory,
        onResetHistoryFilter = viewModel::resetHistoryFilter,
        onClearHistory = viewModel::clearHistory,
        onRetryHistory = viewModel::retryHistory,
        onShowAbout = viewModel::showAbout,
        onDismissAbout = viewModel::dismissAbout,
        surfaceState = surfaceState,
        onRetrySurface = surfaceViewModel::retry,
        snackbarHost = { SnackbarHost(snackbar) },
        exportAction = {
            if (session != null && text != null) {
                val labels = DiagnosticReportLabels(
                    appName = stringResource(R.string.app_name),
                    causes = stringResource(R.string.export_report_causes),
                    anr = stringResource(R.string.export_report_anr),
                    trace = stringResource(R.string.export_report_trace),
                )
                DiagnosticExportAction(session.id, text.ai != null, exportStatus) { destination, includeTrace, includeAdvice ->
                    val report = formatDiagnosticReport(session.analysis, text, labels, includeTrace, includeAdvice)
                    when (destination) {
                        DiagnosticReportDestination.FOLDER -> if (exportViewModel.prepare(report)) {
                            try {
                                exportPicker.launch(null)
                            } catch (_: ActivityNotFoundException) {
                                exportViewModel.onPickerUnavailable()
                            }
                        }
                        DiagnosticReportDestination.SHARE_TEXT -> {
                            val send = Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TITLE, labels.appName)
                                .putExtra(Intent.EXTRA_TEXT, report)
                            try {
                                context.startActivity(Intent.createChooser(send, null))
                            } catch (_: ActivityNotFoundException) {
                                scope.launch { snackbar.showSnackbar(shareUnavailable) }
                            }
                        }
                    }
                }
            }
        },
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
    onCaptureLogcat: () -> Unit,
    onCancelLogcatCapture: () -> Unit,
    onOpenSession: (DiagnosticSession) -> Unit,
    onDeleteSession: (String) -> Unit,
    onHistoryQueryChange: (String) -> Unit,
    onHistoryCategorySelected: (DiagnosticCategory?) -> Unit,
    onResetHistoryFilter: () -> Unit,
    onClearHistory: () -> Unit,
    onRetryHistory: () -> Unit,
    onShowAbout: () -> Unit,
    onDismissAbout: () -> Unit,
    modifier: Modifier = Modifier,
    surfaceState: DiagnosticSurfaceState = DiagnosticSurfaceState(),
    onRetrySurface: () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    exportAction: @Composable () -> Unit = {},
    aiControls: @Composable (DiagnosticSession) -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val resultRequester = remember { BringIntoViewRequester() }
    val historyRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val example = stringResource(R.string.example_stack_trace)
    var sessionPendingDeletion by rememberSaveable { mutableStateOf<String?>(null) }
    var showInputOptions by remember { mutableStateOf(false) }
    val canChangeHistory = !uiState.isInputBusy && !uiState.isHistoryMutating
    var lastRevealedRevision by rememberSaveable(uiState.selectedSession != null) { mutableLongStateOf(Long.MIN_VALUE) }
    var showAnalyzing by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isAnalyzing) {
        showAnalyzing = false
        if (uiState.isAnalyzing) {
            delay(180)
            showAnalyzing = true
        }
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = snackbarHost,
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End),
            ) {
                Row(
                    modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier.size(34.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_trace),
                            contentDescription = null,
                            modifier = Modifier.size(21.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                }
                TextButton(onClick = {
                    keyboard?.hide()
                    focusManager.clearFocus()
                    scope.launch { historyRequester.bringIntoView() }
                }) { Text(stringResource(R.string.history_title)) }
                TextButton(onClick = onShowAbout) { Text(stringResource(R.string.home_info_button)) }
            }
            AnalysisHero(
                importing = uiState.logcatReadInProgress == LogcatSource.DOCUMENT,
                enabled = canChangeHistory,
                onImport = {
                    keyboard?.hide()
                    focusManager.clearFocus()
                    onImportLogcat()
                },
            )
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = { onInputChange(example) },
                            enabled = canChangeHistory,
                            modifier = Modifier.testTag("load_example"),
                        ) { Text(stringResource(R.string.action_load_example)) }
                        Box {
                            TextButton(onClick = { showInputOptions = true }, enabled = canChangeHistory) {
                                Text(stringResource(R.string.home_more_options))
                            }
                            DropdownMenu(
                                expanded = showInputOptions,
                                onDismissRequest = { showInputOptions = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_capture_logcat)) },
                                    onClick = {
                                        showInputOptions = false
                                        keyboard?.hide()
                                        focusManager.clearFocus()
                                        onCaptureLogcat()
                                    },
                                    enabled = canChangeHistory,
                                    modifier = Modifier.testTag("capture_logcat"),
                                )
                            }
                        }
                    }
                    if (uiState.logcatReadInProgress == LogcatSource.DEVICE) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(R.string.action_capturing_logcat),
                                modifier = Modifier.align(Alignment.CenterVertically),
                            )
                            TextButton(onClick = onCancelLogcatCapture) {
                                Text(stringResource(R.string.action_cancel_logcat_capture))
                            }
                        }
                    }
                    uiState.logcatReadError?.let { error ->
                        Text(
                            stringResource(error.messageResource()),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    val inputErrorMessage = when (uiState.inputError) {
                        DiagnosticInputError.EMPTY -> stringResource(R.string.input_error_empty)
                        DiagnosticInputError.TOO_LONG -> stringResource(R.string.input_error_too_long, DiagnosticInput.MAX_LENGTH)
                        null -> null
                    }
                    OutlinedTextField(
                        value = uiState.input,
                        onValueChange = onInputChange,
                        label = { Text(stringResource(R.string.home_input_label)) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            errorContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("diagnostic_input"),
                        minLines = 3,
                        maxLines = 5,
                        isError = uiState.inputError != null,
                        supportingText = if (inputErrorMessage == null) null else { { Text(inputErrorMessage) } },
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                keyboard?.hide()
                                focusManager.clearFocus()
                                onAnalyze()
                            },
                            enabled = !uiState.isInputBusy && !uiState.isHistoryMutating,
                            modifier = Modifier.testTag("analyze_input"),
                        ) {
                            val label = if (uiState.isAnalyzing && showAnalyzing) {
                                R.string.action_analyzing
                            } else {
                                R.string.action_analyze
                            }
                            Text(stringResource(label))
                        }
                        if (uiState.input.isNotEmpty()) {
                            TextButton(
                                onClick = { onInputChange("") },
                                modifier = Modifier.testTag("clear_input"),
                            ) { Text(stringResource(R.string.action_clear_input)) }
                        }
                    }
                    if (uiState.logcatReadInProgress != null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            uiState.operationError?.let { error ->
                Text(
                    stringResource(
                        when (error) {
                            HomeOperationError.ANALYSIS -> R.string.analysis_error
                            HomeOperationError.HISTORY_SAVE -> R.string.history_save_error
                            HomeOperationError.HISTORY_CLEAR -> R.string.history_clear_error
                            HomeOperationError.HISTORY_DELETE -> R.string.history_delete_error
                        },
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            uiState.selectedSession?.let {
                val revision = uiState.surfaceRevision
                DiagnosticResultCard(
                    sessionId = it.id,
                    surfaceState = surfaceState,
                    onRetry = onRetrySurface,
                    modifier = Modifier.bringIntoViewRequester(resultRequester),
                    headerModifier = Modifier.testTag("analysis_heading"),
                    presentationRevision = revision,
                    onPresented = {
                        if (lastRevealedRevision != revision) {
                            withFrameNanos { }
                            resultRequester.bringIntoView()
                            lastRevealedRevision = revision
                        }
                    },
                    actions = exportAction,
                    afterCard = {
                        key(it.id) {
                            var showAi by rememberSaveable { mutableStateOf(false) }
                            TextButton(onClick = { showAi = !showAi }) {
                                Text(stringResource(if (showAi) R.string.ai_hide_options else R.string.ai_title))
                            }
                            if (showAi) aiControls(it)
                        }
                    },
                )
            }
            val showHistoryControls = !uiState.isHistoryLoading && !uiState.historyLoadError &&
                uiState.recentSessions.isNotEmpty()
            Text(
                stringResource(R.string.history_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = if (showHistoryControls) Modifier else Modifier.bringIntoViewRequester(historyRequester),
            )
            if (uiState.isHistoryLoading) {
                Text(stringResource(R.string.history_loading), modifier = Modifier.testTag("history_loading"))
            } else if (uiState.historyLoadError) {
                Text(stringResource(R.string.history_load_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetryHistory, modifier = Modifier.testTag("retry_history")) {
                    Text(stringResource(R.string.action_retry_history))
                }
            } else if (uiState.recentSessions.isEmpty()) {
                Text(
                    stringResource(R.string.history_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
                        .padding(16.dp)
                        .testTag("history_empty"),
                )
            } else {
                HistorySearchControls(
                    filter = uiState.historyFilter,
                    matchCount = uiState.matchingSessions.size.takeUnless { uiState.isHistorySearching },
                    onQueryChange = onHistoryQueryChange,
                    onCategorySelected = onHistoryCategorySelected,
                    onReset = onResetHistoryFilter,
                    modifier = Modifier.bringIntoViewRequester(historyRequester),
                )
                if (!uiState.historyFilter.isActive) {
                    TextButton(
                        onClick = onClearHistory,
                        enabled = canChangeHistory,
                        modifier = Modifier.testTag("clear_history"),
                    ) {
                        Text(stringResource(if (uiState.isClearingHistory) R.string.action_clearing_history else R.string.action_clear_history))
                    }
                }
                if (!uiState.isHistorySearching && uiState.matchingSessions.isEmpty()) {
                    Text(stringResource(R.string.history_no_matches), modifier = Modifier.testTag("history_no_matches"))
                }
                uiState.matchingSessions.forEach { session ->
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth().testTag("history_entry"),
                        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            ) {
                                TextButton(
                                    onClick = {
                                        keyboard?.hide()
                                        focusManager.clearFocus()
                                        onOpenSession(session)
                                    },
                                    modifier = Modifier.testTag("history_open_entry"),
                                ) {
                                    Text(stringResource(R.string.history_open_entry))
                                }
                                TextButton(
                                    onClick = {
                                        keyboard?.hide()
                                        focusManager.clearFocus()
                                        sessionPendingDeletion = session.id
                                    },
                                    enabled = canChangeHistory,
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.testTag("history_delete_entry"),
                                ) {
                                    val label = if (uiState.deletingSessionId == session.id) R.string.history_deleting_entry
                                    else R.string.history_delete_entry
                                    Text(stringResource(label))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    val pendingId = sessionPendingDeletion
    if (pendingId != null && uiState.recentSessions.any { it.id == pendingId }) {
        AlertDialog(
            onDismissRequest = { sessionPendingDeletion = null },
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = { Text(stringResource(R.string.history_delete_description)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        sessionPendingDeletion = null
                        onDeleteSession(pendingId)
                    },
                    enabled = canChangeHistory,
                    modifier = Modifier.testTag("history_confirm_delete"),
                ) { Text(stringResource(R.string.history_delete_confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { sessionPendingDeletion = null },
                    modifier = Modifier.testTag("history_cancel_delete"),
                ) { Text(stringResource(R.string.history_delete_cancel)) }
            },
        )
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

private fun LogcatReadError.messageResource(): Int = when (reason) {
    LogcatIncidentResult.Failure.UNREADABLE -> if (source == LogcatSource.DEVICE) {
        R.string.logcat_capture_unreadable
    } else {
        R.string.logcat_unreadable
    }
    LogcatIncidentResult.Failure.DUMP_TOO_LARGE -> if (source == LogcatSource.DEVICE) {
        R.string.logcat_capture_too_large
    } else {
        R.string.logcat_file_too_large
    }
    LogcatIncidentResult.Failure.INVALID_ENCODING -> if (source == LogcatSource.DEVICE) {
        R.string.logcat_capture_unreadable
    } else {
        R.string.logcat_invalid_encoding
    }
    LogcatIncidentResult.Failure.NO_INCIDENT -> if (source == LogcatSource.DEVICE) {
        R.string.logcat_capture_no_incident
    } else {
        R.string.logcat_no_incident
    }
    LogcatIncidentResult.Failure.INCIDENT_TOO_LONG -> R.string.logcat_incident_too_long
    LogcatIncidentResult.Failure.TIMED_OUT -> R.string.logcat_capture_timeout
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    DebugLensTheme {
        HomeScreen(
            uiState = HomeUiState(),
            onInputChange = {},
            onAnalyze = {},
            onImportLogcat = {},
            onCaptureLogcat = {},
            onCancelLogcatCapture = {},
            onOpenSession = {},
            onDeleteSession = {},
            onHistoryQueryChange = {},
            onHistoryCategorySelected = {},
            onResetHistoryFilter = {},
            onClearHistory = {},
            onRetryHistory = {},
            onShowAbout = {},
            onDismissAbout = {},
        )
    }
}
