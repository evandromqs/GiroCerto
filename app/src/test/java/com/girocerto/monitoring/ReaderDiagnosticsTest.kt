package com.girocerto.monitoring

import org.junit.Assert.*
import org.junit.Test

class ReaderDiagnosticsTest {
    @Test fun nestedCauseKeepsLocationButNotPrivateMessage() {
        val cause = IllegalArgumentException("endereço privado")
        cause.stackTrace = arrayOf(StackTraceElement("Bitmap", "copy", "Bitmap.java", 100))
        val report = ReaderDiagnostics.record("conversão", IllegalStateException("texto privado", cause))
        assertTrue(report.contains("IllegalArgumentException"))
        assertTrue(report.contains("Bitmap.copy(Bitmap.java:100)"))
        assertFalse(report.contains("privado"))
    }
    @Test fun preservesFailureLocationWithoutCopyingScreenContent() {
        val error = NullPointerException("Rua particular, passageiro, valor")
        error.stackTrace = arrayOf(StackTraceElement("Reader", "read", "Reader.kt", 42))
        val report = ReaderDiagnostics.record("OCR", error)
        assertTrue(report.contains("NullPointerException"))
        assertTrue(report.contains("Reader.read(Reader.kt:42)"))
        assertFalse(report.contains("particular"))
        assertEquals(report, ReaderDiagnostics.lastFailure)
    }
}
