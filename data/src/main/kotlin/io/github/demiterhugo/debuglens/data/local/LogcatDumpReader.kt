package io.github.demiterhugo.debuglens.data.local

import io.github.demiterhugo.debuglens.domain.repository.LogcatIncidentResult
import io.github.demiterhugo.debuglens.domain.usecase.ExtractLogcatIncidentUseCase
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException

internal object LogcatDumpReader {
    private const val MAX_DUMP_BYTES = 2 * 1024 * 1024
    private val extractIncident = ExtractLogcatIncidentUseCase()

    fun readDocument(stream: InputStream): LogcatIncidentResult = readIncident(stream) { bytes ->
        val bom = if (bytes.remaining() >= 2) bytes.getShort(0).toInt() and 0xFFFF else 0
        val charset = if (bom == 0xFEFF || bom == 0xFFFE) Charsets.UTF_16 else Charsets.UTF_8
        charset.newDecoder().decode(bytes).toString()
    }

    fun readCapture(stream: InputStream): LogcatIncidentResult = readIncident(stream) { bytes ->
        // A live dump can contain malformed bytes in an unrelated entry.
        Charsets.UTF_8.decode(bytes).toString().replace('\u0000', '\uFFFD')
    }

    private inline fun readIncident(
        stream: InputStream,
        decode: (ByteBuffer) -> String,
    ): LogcatIncidentResult {
        val buffer = ByteArray(MAX_DUMP_BYTES + 1)
        var size = 0
        while (size < buffer.size) {
            val read = stream.read(buffer, size, buffer.size - size)
            if (read == -1) break
            if (read == 0) throw IOException("The Logcat stream did not make progress")
            size += read
        }
        if (size > MAX_DUMP_BYTES) return LogcatIncidentResult.Failure.DUMP_TOO_LARGE

        val text = try {
            decode(ByteBuffer.wrap(buffer, 0, size)).removePrefix("\uFEFF")
        } catch (_: CharacterCodingException) {
            return LogcatIncidentResult.Failure.INVALID_ENCODING
        }
        if ('\u0000' in text) return LogcatIncidentResult.Failure.INVALID_ENCODING
        return extractIncident(text)
    }
}
