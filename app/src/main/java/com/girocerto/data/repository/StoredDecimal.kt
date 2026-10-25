package com.girocerto.data.repository

internal object StoredDecimal {
    fun decode(value: Any?, default: Double): Double =
        value?.toString()?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: default
}
