package com.girocerto.overlay

import com.girocerto.domain.model.RideAnalysis

/** Session-only observations, not a promise that the platform still offers the trip. */
internal class RecentOffers(private val defaultLifetimeMs: Long = 15_000L) {
    data class Entry(val analysis: RideAnalysis, val firstSeen: Long, val lifetimeMs: Long) {
        fun remainingFraction(now: Long) = (1f - (now - firstSeen).toFloat() / lifetimeMs).coerceIn(0f, 1f)
    }
    private val entries = linkedMapOf<String, Entry>()
    private data class Observation(val offer: com.girocerto.domain.model.RideOffer, val time: Long)
    // Repeated observations cannot resurrect an expired offer. After two minutes
    // without observations, an identical fare/route can legitimately be a new trip.
    private val lastSeen = mutableMapOf<String, Observation>()

    fun add(analysis: RideAnalysis, now: Long, lifetimeMs: Long = defaultLifetimeMs): Boolean {
        lastSeen.entries.removeAll { now - it.value.time >= 120_000L }
        val offer = analysis.offer
        val key = when {
            offer.appName.contains("uber", true) -> "uber"
            offer.appName.contains("99") || offer.appName.contains("didi", true) -> "99"
            offer.appName.contains("ifood", true) -> "ifood"
            else -> offer.appName.lowercase()
        }
        val previous = lastSeen.put(key, Observation(offer, now))
        if (previous?.offer == offer) return false
        entries.remove(key)
        entries[key] = Entry(analysis, now, lifetimeMs.coerceIn(15_000L, 30_000L))
        while (entries.size > 3) entries.remove(entries.keys.first())
        return true
    }

    fun active(now: Long): List<Entry> {
        entries.entries.removeAll { now - it.value.firstSeen >= it.value.lifetimeMs }
        return entries.values.toList().asReversed()
    }

    fun clear() { entries.clear(); lastSeen.clear() }
}
