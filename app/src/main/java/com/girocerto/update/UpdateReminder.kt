package com.girocerto.update

internal object UpdateReminder {
    const val DELAY_MS = 86_400_000L
    fun shouldPrompt(version: Long, dismissedVersion: Long, remindAfter: Long, now: Long) =
        version != dismissedVersion || now >= remindAfter
}
