package com.girocerto.update
import org.junit.Assert.*
import org.junit.Test
class UpdateReminderTest {
    @Test fun dismissedVersionWaitsUntilDeadline() {
        assertFalse(UpdateReminder.shouldPrompt(8, 8, 1000, 999))
        assertTrue(UpdateReminder.shouldPrompt(8, 8, 1000, 1000))
    }
    @Test fun differentReleaseIsNotSilenced() {
        assertTrue(UpdateReminder.shouldPrompt(9, 8, 1000, 999))
    }
}
