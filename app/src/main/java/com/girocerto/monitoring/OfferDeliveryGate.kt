package com.girocerto.monitoring

/** Suppresses repeat frames independently for each platform, across OCR and accessibility. */
internal class OfferDeliveryGate(private val intervalMs: Long = 15_000L) {
    private data class Entry(val result: OfferReaderResult, val time: Long)
    private val recent = mutableMapOf<String, Entry>()

    fun accepts(packageName: String, result: OfferReaderResult, now: Long): Boolean {
        if (!result.isComplete) return false
        recent.entries.removeAll { now - it.value.time >= intervalMs }
        // The same offer may be recognized by either reader. Its source is not its identity.
        val identity = result.copy(source = OfferSource.ACCESSIBILITY_SERVICE)
        val previous = recent[packageName]
        recent[packageName] = Entry(identity, now)
        // Continuous polling must not reopen a card the user has already collapsed.
        return previous?.result != identity
    }

    private val recentKeys = mutableMapOf<String, Long>()

    fun permits(offerKey: String, now: Long = System.currentTimeMillis()): Boolean {
        recentKeys.entries.removeAll { now - it.value >= intervalMs }
        val lastSeen = recentKeys[offerKey]
        if (lastSeen != null && now - lastSeen < intervalMs) {
            return false
        }
        recentKeys[offerKey] = now
        return true
    }
}
