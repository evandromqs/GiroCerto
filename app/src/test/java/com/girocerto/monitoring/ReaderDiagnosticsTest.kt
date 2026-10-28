package com.girocerto.monitoring

import org.junit.Assert.*
import org.junit.Test

class ReaderDiagnosticsTest {
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
