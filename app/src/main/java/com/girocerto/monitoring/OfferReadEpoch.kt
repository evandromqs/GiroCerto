package com.girocerto.monitoring

/** A window ID identifies a surface, not the offer currently displayed on it. */
internal class OfferReadEpoch(private val maximumAgeMs: Long = 2_500L) {
    private val generations = mutableMapOf<String, Long>()
    fun current(pkg: String) = generations[pkg] ?: 0L
    fun invalidate(pkg: String) { generations[pkg] = current(pkg) + 1 }
    fun accepts(pkg: String, generation: Long, started: Long, now: Long) =
        generation == current(pkg) && now - started in 0..maximumAgeMs
}
