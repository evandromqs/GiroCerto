package com.girocerto.monitoring

/** Guarantees that repeated accessibility/OCR frames announce one offer only once. */
internal class VoiceAnnouncementGate(private val retentionMs: Long = 10 * 60_000L) {
    private val announced = LinkedHashMap<String, Long>()

    @Synchronized
    fun accepts(identity: String, now: Long): Boolean {
        announced.entries.removeAll { now - it.value >= retentionMs }
        if (announced.containsKey(identity)) return false
        announced[identity] = now
        while (announced.size > 100) announced.remove(announced.keys.first())
        return true
    }
}
