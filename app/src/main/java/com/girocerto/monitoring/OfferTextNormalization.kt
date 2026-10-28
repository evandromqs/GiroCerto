package com.girocerto.monitoring

/** Preserve digits and units while removing invisible formatting around currency. */
internal object OfferTextNormalization {
    fun lines(raw: List<String>): List<String> = raw.flatMap { it.lines() }.map { line ->
        line.replace(Regex("\\p{Cf}"), "")
            .replace('\u00A0', ' ').replace('\u202F', ' ').trim()
    }.filter { it.isNotBlank() }
}
