package io.github.demiterhugo.debuglens.data.local

import androidx.room3.Database
import androidx.room3.RoomDatabase

@Database(
    entities = [DiagnosticSessionEntity::class, DiagnosticCauseEntity::class],
    version = 1,
    exportSchema = true,
)
internal abstract class DiagnosticHistoryDatabase : RoomDatabase() {
    abstract fun diagnosticHistoryDao(): DiagnosticHistoryDao
}
