package com.girocerto.overlay

import com.girocerto.domain.model.RideAnalysis

/** Session-only observations, not a promise that the platform still offers the trip. */
internal class RecentOffers {
    data class Entry(val analysis: RideAnalysis, val firstSeen: Long)
    private val entries = linkedMapOf<String, Entry>()
    // Remember expired identities so a repeated frame cannot resurrect an expired offer.
    private val lastSeen = mutableMapOf<String, com.girocerto.domain.model.RideOffer>()

    fun add(analysis: RideAnalysis, now: Long): Boolean {
        val offer = analysis.offer
        val key = when {
            offer.appName.contains("uber", true) -> "uber"
            offer.appName.contains("99") || offer.appName.contains("didi", true) -> "99"
            offer.appName.contains("ifood", true) -> "ifood"
            else -> offer.appName.lowercase()
        }
        if (lastSeen[key] == offer) return false
        lastSeen[key] = offer
        entries.remove(key)
        entries[key] = Entry(analysis, now)
        while (entries.size > 3) entries.remove(entries.keys.first())
        return true
    }

    fun active(now: Long): List<Entry> {
        entries.entries.removeAll { now - it.value.firstSeen >= 30_000L }
        return entries.values.toList().asReversed()
    }

    fun clear() { entries.clear(); lastSeen.clear() }
}
