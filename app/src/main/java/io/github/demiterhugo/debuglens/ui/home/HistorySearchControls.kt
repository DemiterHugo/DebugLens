package io.github.demiterhugo.debuglens.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.demiterhugo.debuglens.R
import io.github.demiterhugo.debuglens.domain.model.DiagnosticCategory
import io.github.demiterhugo.debuglens.domain.usecase.DiagnosticHistoryFilter
import io.github.demiterhugo.debuglens.ui.diagnostic.titleResource

@Composable
internal fun HistorySearchControls(
    filter: DiagnosticHistoryFilter,
    matchCount: Int?,
    onQueryChange: (String) -> Unit,
    onCategorySelected: (DiagnosticCategory?) -> Unit,
    onReset: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var categoriesExpanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = filter.query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(R.string.history_search)) },
            placeholder = { Text(stringResource(R.string.history_search_hint)) },
            modifier = Modifier.fillMaxWidth().testTag("history_query"),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        )
        Box {
            OutlinedButton(
                onClick = {
                    focusManager.clearFocus()
                    categoriesExpanded = true
                },
                modifier = Modifier.testTag("history_category"),
            ) {
                val category = filter.category
                Text(
                    if (category == null) stringResource(R.string.history_all_categories)
                    else stringResource(R.string.history_selected_category, stringResource(category.titleResource)),
                )
            }
            DropdownMenu(expanded = categoriesExpanded, onDismissRequest = { categoriesExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.history_all_categories)) },
                    onClick = {
                        onCategorySelected(null)
                        categoriesExpanded = false
                    },
                    modifier = Modifier.testTag("history_category_all"),
                )
                DiagnosticCategory.entries.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(stringResource(category.titleResource)) },
                        onClick = {
                            onCategorySelected(category)
                            categoriesExpanded = false
                        },
                        modifier = Modifier.testTag("history_category_${category.name}"),
                    )
                }
            }
        }
        if (filter.isActive) {
            Text(
                if (matchCount == null) stringResource(R.string.history_searching)
                else pluralStringResource(R.plurals.history_matches, matchCount, matchCount),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onReset, modifier = Modifier.testTag("history_reset_filters")) {
                Text(stringResource(R.string.history_reset_filters))
            }
        }
    }
}
