package io.github.demiterhugo.debuglens.data.local

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Relation

@Entity(tableName = "diagnostic_sessions", indices = [Index(value = ["id"], unique = true)])
internal data class DiagnosticSessionEntity(
    val id: String,
    val input: String,
    val category: String,
    @Embedded(prefix = "location_") val location: DiagnosticLocationEntity?,
    val anrEvidence: String?,
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
)

internal data class DiagnosticLocationEntity(
    val symbol: String,
    val fileName: String?,
    val lineNumber: Int?,
)

@Entity(
    tableName = "diagnostic_causes",
    primaryKeys = ["sessionId", "position"],
    foreignKeys = [ForeignKey(
        entity = DiagnosticSessionEntity::class,
        parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE,
    )],
)
internal data class DiagnosticCauseEntity(
    val sessionId: String,
    val position: Int,
    val type: String,
    val message: String?,
)

internal data class StoredDiagnosticSession(
    @Embedded val session: DiagnosticSessionEntity,
    @Relation(parentColumns = ["id"], entityColumns = ["sessionId"])
    val causes: List<DiagnosticCauseEntity>,
)
