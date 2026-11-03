package io.github.demiterhugo.debuglens.data.local

import io.github.demiterhugo.debuglens.domain.model.DiagnosticAnalysis
import io.github.demiterhugo.debuglens.domain.model.DiagnosticCategory
import io.github.demiterhugo.debuglens.domain.model.DiagnosticInput
import io.github.demiterhugo.debuglens.domain.model.DiagnosticInputResult
import io.github.demiterhugo.debuglens.domain.model.DiagnosticSession
import io.github.demiterhugo.debuglens.domain.model.ExceptionSummary
import io.github.demiterhugo.debuglens.domain.model.StackFrame

internal fun DiagnosticSession.toStored(): StoredDiagnosticSession = StoredDiagnosticSession(
    session = DiagnosticSessionEntity(
        id = id,
        input = analysis.input.text,
        category = analysis.category.name,
        location = analysis.location?.let { DiagnosticLocationEntity(it.symbol, it.fileName, it.lineNumber) },
        anrEvidence = analysis.anrEvidence,
    ),
    causes = analysis.causes.mapIndexed { index, cause -> DiagnosticCauseEntity(id, index, cause.type, cause.message) },
)

internal fun StoredDiagnosticSession.toDomain(): DiagnosticSession {
    val input = (DiagnosticInput.parse(session.input) as? DiagnosticInputResult.Valid)?.input
        ?: error("Stored diagnostic input is invalid")
    return DiagnosticSession(
        id = session.id,
        analysis = DiagnosticAnalysis(
            input = input,
            category = DiagnosticCategory.entries.firstOrNull { it.name == session.category } ?: DiagnosticCategory.UNKNOWN,
            causes = causes.sortedBy { it.position }.map { ExceptionSummary(it.type, it.message) },
            location = session.location?.let { StackFrame(it.symbol, it.fileName, it.lineNumber) },
            anrEvidence = session.anrEvidence,
        ),
    )
}
