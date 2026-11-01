package com.girocerto.monitoring

/** Short-lived ownership evidence from accessibility events, never inferred from screen text. */
class WindowOwnerHints {
    private data class Hint(val pkg: String, val seen: Long)
    private val hints = mutableMapOf<Int, Hint>()
    fun observe(id: Int, pkg: String, now: Long) {
        hints.entries.removeAll { now - it.value.seen > 10_000 }
        if (id >= 0) hints[id] = Hint(pkg, now)
    }
    fun owner(id: Int, rootPackage: String?, now: Long): String? {
        if (rootPackage != null) {
            // An explicit owner always overrides stale events, including window-ID reuse.
            hints[id] = Hint(rootPackage, now)
            return rootPackage
        }
        return hints[id]?.takeIf { now - it.seen in 0..10_000 }?.pkg
    }
}
