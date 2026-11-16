package io.github.demiterhugo.debuglens.ui.diagnostic

import androidx.a2ui.compose.runtime.A2uiComponentProperties
import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.runtime.A2uiComponentState
import androidx.a2ui.compose.runtime.A2uiProperty
import androidx.a2ui.compose.runtime.observeA2uiComponentState
import androidx.a2ui.compose.ui.A2uiCatalog
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.demiterhugo.debuglens.R

internal val LocalDiagnosticHeadingModifier = staticCompositionLocalOf<Modifier> { Modifier }

internal val DiagnosticCatalog: A2uiCatalog = A2uiCatalog(
    catalogId = DIAGNOSTIC_CATALOG_ID,
    components = listOf(DiagnosticText, DiagnosticColumn, DiagnosticButton, DiagnosticCheckBox),
)

private object DiagnosticText : A2uiComponent {
    private val text = A2uiProperty.dynamicString("text", required = true)
    private val variant = A2uiProperty.stringEnum(
        "variant", enumValues = listOf("title", "section", "body", "code", "label", "evidence"),
    )
    override val name = "Text"
    override val description = "Plain diagnostic text with a registered typography style."
    override val properties = listOf(text, variant)

    @Composable
    override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean = properties.bind(text) != null

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val value = properties.bind(text) ?: return
        val style = properties[variant] ?: "body"
        val typography = when (style) {
            "title" -> MaterialTheme.typography.headlineMedium
            "section" -> MaterialTheme.typography.titleSmall
            "code" -> MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            "evidence" -> MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
            "label" -> MaterialTheme.typography.labelMedium
            else -> MaterialTheme.typography.bodyMedium
        }
        val textModifier = when (style) {
            "title" -> modifier.then(LocalDiagnosticHeadingModifier.current)
            "evidence" -> modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.shapes.small)
                .padding(horizontal = 12.dp, vertical = 9.dp)
            else -> modifier
        }
        SelectionContainer {
            Text(
                text = value,
                style = typography,
                modifier = textModifier,
                color = when (style) {
                    "label" -> MaterialTheme.colorScheme.onSurfaceVariant
                    "evidence" -> MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.onSurface
                },
                maxLines = if (style == "code") 16 else 12,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private object DiagnosticColumn : A2uiComponent {
    private val children = A2uiProperty.childList("children", required = true)
    override val name = "Column"
    override val description = "A vertical diagnostic layout containing registered component references."
    override val properties = listOf(children)

    @Composable
    override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean {
        val references = properties.bindChildReferences(children) ?: return false
        return references.none { observeA2uiComponentState(it) is A2uiComponentState.Loading }
    }

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val references = properties.bindChildReferences(children) ?: return
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            references.forEach { reference ->
                key(reference.id, reference.baseDataPath) {
                    when (val state = observeA2uiComponentState(reference)) {
                        is A2uiComponentState.Success -> A2uiComponent(state.component)
                        is A2uiComponentState.Error -> Text(stringResource(R.string.a2ui_component_error))
                        A2uiComponentState.Loading -> Text(stringResource(R.string.a2ui_loading))
                    }
                }
            }
        }
    }
}

private object DiagnosticButton : A2uiComponent {
    private val label = A2uiProperty.dynamicString("label", required = true)
    private val action = A2uiProperty.action("action", required = true)
    override val name = "Button"
    override val description = "Dispatches a registered local diagnostic action."
    override val properties = listOf(label, action)

    @Composable
    override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean = properties.bind(label) != null

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val value = properties.bind(label) ?: return
        val definition = properties[action] ?: return
        TextButton(onClick = { dispatchAction(definition) }, modifier = modifier) { Text(value) }
    }
}

private object DiagnosticCheckBox : A2uiComponent {
    private val label = A2uiProperty.dynamicString("label", required = true)
    private val value = A2uiProperty.dynamicBoolean("value", required = true)
    override val name = "CheckBox"
    override val description = "Tracks a diagnostic review step through a two-way boolean data binding."
    override val properties = listOf(label, value)

    @Composable
    override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
        properties.bind(label) != null && properties.bind(value) != null

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val text = properties.bind(label) ?: return
        val checked = properties.bind(value) ?: return
        val update = properties.bindUpdater(value)
        Row(
            modifier = modifier.fillMaxWidth().testTag("diagnostic_checkbox").toggleable(
                value = checked, enabled = update != null, role = Role.Checkbox,
                onValueChange = { update?.invoke(it) },
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = null, enabled = update != null)
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
