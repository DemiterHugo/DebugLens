package io.github.demiterhugo.debuglens.data.repository

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import io.github.demiterhugo.debuglens.data.local.DiagnosticHistoryDatabase
import io.github.demiterhugo.debuglens.domain.repository.DiagnosticHistoryRepository
import java.io.Closeable

/** Owns the database without exposing Room types outside the data module. */
class DiagnosticHistoryStorage private constructor(private val database: DiagnosticHistoryDatabase) : Closeable {
    val repository: DiagnosticHistoryRepository = RoomDiagnosticHistoryRepository(database.diagnosticHistoryDao())

    override fun close() = database.close()

    companion object {
        fun open(context: Context): DiagnosticHistoryStorage = open(context, "diagnostic-history.db")

        internal fun open(context: Context, databaseName: String): DiagnosticHistoryStorage = DiagnosticHistoryStorage(
            Room.databaseBuilder(context.applicationContext, DiagnosticHistoryDatabase::class.java, databaseName)
                .setDriver(AndroidSQLiteDriver())
                .build(),
        )
    }
}
