package com.girocerto.monitoring

/** Keep code locations only: exception messages can contain screen content. */
internal object ReaderDiagnostics {
    @Volatile var lastFailure: String = "Nenhuma falha registrada nesta sessão"
        private set

    fun record(stage: String, error: Exception): String {
        val trace = error.stackTrace.take(8).joinToString("\n") { frame ->
            "${frame.className}.${frame.methodName}(${frame.fileName}:${frame.lineNumber})"
        }
        return "$stage: ${error.javaClass.simpleName}\n$trace".also { lastFailure = it }
    }
}
