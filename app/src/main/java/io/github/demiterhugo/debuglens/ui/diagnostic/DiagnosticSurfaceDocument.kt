package io.github.demiterhugo.debuglens.ui.diagnostic

import androidx.a2ui.model.protocol.A2uiComponentPayload
import io.github.demiterhugo.debuglens.domain.model.DiagnosticAnalysis

internal const val DIAGNOSTIC_CATALOG_ID = "urn:debuglens:catalog:diagnostics:1"
internal const val TOGGLE_CAUSES = "toggle_causes"
internal const val TOGGLE_SOURCE = "toggle_source"

internal data class DiagnosticSurfaceCopy(
    val label: String,
    val title: String,
    val limitations: String,
    val location: String,
    val guidanceTitle: String,
    val guidance: String,
    val checklistTitle: String,
    val checklist: List<String>,
    val showCauses: String,
    val hideCauses: String,
    val causesTitle: String,
    val causesOmitted: String,
    val showSource: String,
    val hideSource: String,
    val sourceOmitted: String,
    val ai: AiDiagnosticCopy? = null,
)

internal data class AiDiagnosticCopy(val title: String, val advice: String, val note: String)

internal data class DiagnosticSurfaceDocument(
    val components: List<A2uiComponentPayload>,
    val initialData: Map<String, Any?>,
)

/** Adapts domain results to the registered A2UI catalog, without interpreting input as UI code. */
internal class DiagnosticSurfaceFactory {
    fun create(
        analysis: DiagnosticAnalysis,
        copy: DiagnosticSurfaceCopy,
        causesExpanded: Boolean = false,
        sourceExpanded: Boolean = false,
    ): DiagnosticSurfaceDocument {
        val components = mutableListOf<A2uiComponentPayload>()
        val children = mutableListOf<String>()

        fun text(id: String, value: String, variant: String = "body", limit: Int = MAX_TEXT_LENGTH) {
            children += id
            components += A2uiComponentPayload(
                id, "Text", mapOf("text" to preview(value, limit), "variant" to variant),
            )
        }

        fun button(id: String, label: String, event: String) {
            children += id
            components += A2uiComponentPayload(
                id, "Button", mapOf("label" to label, "action" to mapOf("event" to mapOf("name" to event))),
            )
        }

        text("label", copy.label, "label")
        text("title", copy.title, "title")
        text("limitations", copy.limitations, "label")
        analysis.rootCause?.let { cause ->
            text("root_type", cause.type, "code")
            cause.message?.let { text("root_message", it) }
        }
        analysis.location?.let { location ->
            text("location_title", copy.location, "section")
            text("location_symbol", location.symbol, "code")
            location.fileName?.let { file ->
                text("location_source", location.lineNumber?.let { "$file:$it" } ?: file, "code")
            }
        }
        if (analysis.rootCause == null) {
            analysis.anrEvidence?.let { text("anr_evidence", it, "code") }
        }
        text("guidance_title", copy.guidanceTitle, "section")
        text("guidance", copy.guidance)
        copy.ai?.let {
            text("ai_title", it.title, "section")
            text("ai_advice", it.advice)
            text("ai_note", it.note, "label")
        }
        text("checklist_title", copy.checklistTitle, "section")
        copy.checklist.forEachIndexed { index, label ->
            val id = "check_$index"
            children += id
            components += A2uiComponentPayload(
                id, "CheckBox", mapOf("label" to label, "value" to mapOf("path" to "/checks/$id")),
            )
        }
        if (analysis.causes.size > 1) {
            text("causes_count", copy.causesTitle, "label")
            button("toggle_causes", if (causesExpanded) copy.hideCauses else copy.showCauses, TOGGLE_CAUSES)
            if (causesExpanded) {
                if (analysis.causes.size > MAX_VISIBLE_CAUSES) text("causes_omitted", copy.causesOmitted, "label")
                analysis.causes.takeLast(MAX_VISIBLE_CAUSES).forEachIndexed { index, cause ->
                    text("cause_${index}_type", cause.type, "code")
                    cause.message?.let { text("cause_${index}_message", it) }
                }
            }
        }
        button("toggle_source", if (sourceExpanded) copy.hideSource else copy.showSource, TOGGLE_SOURCE)
        if (sourceExpanded) {
            text("source", analysis.input.text, "code", MAX_SOURCE_LENGTH)
            if (analysis.input.text.length > MAX_SOURCE_LENGTH) text("source_omitted", copy.sourceOmitted, "label")
        }
        components += A2uiComponentPayload("root", "Column", mapOf("children" to children.toList()))
        return DiagnosticSurfaceDocument(
            components = components.toList(),
            initialData = mapOf("checks" to copy.checklist.indices.associate { "check_$it" to false }),
        )
    }

    private fun preview(value: String, limit: Int): String {
        if (value.length <= limit) return value
        val end = if (value[limit - 1].isHighSurrogate() && value[limit].isLowSurrogate()) limit - 1 else limit
        return value.take(end) + "…"
    }

    companion object {
        const val MAX_VISIBLE_CAUSES = 8
        const val MAX_TEXT_LENGTH = 2_048
        const val MAX_SOURCE_LENGTH = 8_192
    }
}
