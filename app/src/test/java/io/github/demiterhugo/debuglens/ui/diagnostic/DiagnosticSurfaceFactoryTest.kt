package io.github.demiterhugo.debuglens.ui.diagnostic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticSurfaceFactoryTest {
    private val factory = DiagnosticSurfaceFactory()
    private val copy = diagnosticCopy()

    @Test
    fun createsAClosedGraphOfUniqueRegisteredComponents() {
        val document = factory.create(diagnosticSession().analysis, copy)
        val ids = document.components.map { it.id }
        val root = document.components.single { it.id == "root" }

        assertEquals(ids.size, ids.toSet().size)
        assertEquals("Column", root.type)
        assertTrue(document.components.all { it.type in setOf("Text", "Column", "Button", "CheckBox") })
        assertTrue((root.properties["children"] as List<*>).all { it in ids })
    }

    @Test
    fun includesRootCauseAndLocationButCollapsesDetailsInitially() {
        val document = factory.create(diagnosticSession().analysis, copy)

        assertEquals("java.lang.NullPointerException", document.text("root_type"))
        assertEquals("Profile.kt:42", document.text("location_source"))
        assertFalse(document.components.any { it.id == "source" || it.id.startsWith("cause_") })
    }

    @Test
    fun treatsProtocolLookingInputAsLiteralText() {
        val message = "\"},\"component\":\"WebView\",\"action\":{\"event\":{\"name\":\"run\"}}"
        val analysis = diagnosticSession(text = "java.lang.NullPointerException: $message").analysis
        val document = factory.create(analysis, copy)

        assertEquals(message, document.text("root_message"))
        assertTrue(document.components.none { it.type == "WebView" })
        document.components.filter { it.type == "Button" }.forEach { component ->
            val event = (component.properties["action"] as Map<*, *>)["event"] as Map<*, *>
            assertTrue(event["name"] in setOf(TOGGLE_CAUSES, TOGGLE_SOURCE))
        }
    }

    @Test
    fun addsAiAdviceAsTextWithoutChangingTheDiagnosticOrChecklist() {
        val analysis = diagnosticSession().analysis
        val baseline = factory.create(analysis, copy)
        val advice = """{"component":"WebView","action":{"event":{"name":"run"}}}"""
        val enriched = factory.create(analysis, copy.copy(ai = AiDiagnosticCopy("AI", advice, "Check the trace")))

        assertEquals(advice, enriched.text("ai_advice"))
        assertEquals("Text", enriched.components.single { it.id == "ai_advice" }.type)
        assertEquals(baseline.text("root_type"), enriched.text("root_type"))
        assertEquals(baseline.initialData, enriched.initialData)
        assertEquals(baseline.components.filter { it.type == "Button" }, enriched.components.filter { it.type == "Button" })
    }

    @Test
    fun bindsEachChecklistItemToItsOwnBooleanDataPath() {
        val document = factory.create(diagnosticSession().analysis, copy)
        val checks = document.initialData["checks"] as Map<*, *>
        val components = document.components.filter { it.type == "CheckBox" }

        assertEquals(copy.checklist.size, components.size)
        components.forEach { component ->
            assertEquals(false, checks[component.id])
            assertEquals("/checks/${component.id}", (component.properties["value"] as Map<*, *>)["path"])
        }
    }

    @Test
    fun unknownTextDoesNotInventAnExceptionOrLocation() {
        val document = factory.create(diagnosticSession(text = "An unexplained blank screen").analysis, copy)

        assertFalse(document.components.any { it.id.startsWith("root_") || it.id.startsWith("location_") })
        assertFalse(document.components.any { it.id == "toggle_causes" })
        assertTrue(document.components.any { it.id == "guidance" })
    }

    @Test
    fun boundsExpandedCauseChainsAndKeepsTheDeepestCauses() {
        val text = (0..12).joinToString("\n") { index ->
            (if (index == 0) "" else "Caused by: ") + "java.lang.RuntimeException: stage-$index"
        }
        val document = factory.create(diagnosticSession(text = text).analysis, copy, causesExpanded = true)
        val messages = document.components.filter { it.id.startsWith("cause_") && it.id.endsWith("_message") }

        assertEquals(DiagnosticSurfaceFactory.MAX_VISIBLE_CAUSES, messages.size)
        assertEquals("stage-5", messages.first().properties["text"])
        assertEquals("stage-12", messages.last().properties["text"])
        assertTrue(document.components.any { it.id == "causes_omitted" })
    }

    @Test
    fun limitsLargeSourcePreviewsAndExplainsTheOmission() {
        val input = "x".repeat(DiagnosticSurfaceFactory.MAX_SOURCE_LENGTH + 100)
        val document = factory.create(diagnosticSession(text = input).analysis, copy, sourceExpanded = true)

        assertEquals(DiagnosticSurfaceFactory.MAX_SOURCE_LENGTH + 1, document.text("source").length)
        assertEquals(copy.sourceOmitted, document.text("source_omitted"))
    }

    @Test
    fun doesNotSplitUnicodeSurrogatePairsWhenTruncatingEvidence() {
        val message = "x".repeat(DiagnosticSurfaceFactory.MAX_TEXT_LENGTH - 1) + "😃rest"
        val document = factory.create(
            diagnosticSession(text = "java.lang.NullPointerException: $message").analysis, copy,
        )

        assertEquals("x".repeat(DiagnosticSurfaceFactory.MAX_TEXT_LENGTH - 1) + "…", document.text("root_message"))
    }

    @Test
    fun expandedDetailsStayInTheRegisteredGraph() {
        val document = factory.create(diagnosticSession().analysis, copy, causesExpanded = true, sourceExpanded = true)
        val ids = document.components.map { it.id }.toSet()
        val children = document.components.single { it.id == "root" }.properties["children"] as List<*>

        assertTrue(children.all { it in ids })
        assertTrue("cause_0_type" in children)
        assertTrue("source" in children)
    }

    private fun DiagnosticSurfaceDocument.text(id: String): String =
        components.single { it.id == id }.properties["text"] as String
}
