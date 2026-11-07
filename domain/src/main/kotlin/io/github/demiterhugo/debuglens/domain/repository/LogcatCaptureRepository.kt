package io.github.demiterhugo.debuglens.domain.repository

fun interface LogcatCaptureRepository {
    /** Reads the latest incident visible to the app from the device's Logcat buffers. */
    suspend fun captureLatestIncident(): LogcatIncidentResult
}
