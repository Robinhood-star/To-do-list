package com.personal.todo

import com.personal.todo.domain.AppConfig
import com.personal.todo.domain.Retention
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetentionTest {
    private val day = 86_400_000L
    private val now = 1_800_000_000_000L

    @Test fun defaultIsThirtyDays() = assertEquals(30, AppConfig.DEFAULT_RETENTION_DAYS)
    @Test fun cutoffIsNowMinusDays() = assertEquals(now - 30 * day, Retention.cutoffMillis(now, 30))

    @Test fun completedOlderThanRetentionIsExpired() {
        assertTrue(Retention.isExpired(now - 31 * day, now, 30))
        assertFalse(Retention.isExpired(now - 29 * day, now, 30))
    }

    @Test fun activeTasksAreNeverExpired() = assertFalse(Retention.isExpired(null, now, 30))
    @Test fun retentionBelowOneDayIsClamped() = assertEquals(now - day, Retention.cutoffMillis(now, 0))
}
