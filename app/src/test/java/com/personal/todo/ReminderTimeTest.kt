package com.personal.todo

import com.personal.todo.data.TaskEntity
import com.personal.todo.domain.ReminderTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReminderTimeTest {
    private val zone = ZoneId.of("UTC")
    private val day = LocalDate.of(2026, 10, 5)

    private fun millis(h: Int, m: Int) = day.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    @Test fun atDueTime() {
        val t = TaskEntity(title = "x", dueDate = day.toEpochDay(), dueTime = 10 * 60, reminderEnabled = true)
        assertEquals(millis(10, 0), ReminderTime.triggerMillis(t, zone))
    }

    @Test fun offsetSubtracts() {
        val t = TaskEntity(title = "x", dueDate = day.toEpochDay(), dueTime = 10 * 60, reminderEnabled = true, reminderOffsetMinutes = 15)
        assertEquals(millis(9, 45), ReminderTime.triggerMillis(t, zone))
    }

    @Test fun noTimeUsesNineAm() {
        val t = TaskEntity(title = "x", dueDate = day.toEpochDay(), reminderEnabled = true)
        assertEquals(millis(9, 0), ReminderTime.triggerMillis(t, zone))
    }

    @Test fun disabledOrUndatedHasNoTrigger() {
        assertNull(ReminderTime.triggerMillis(TaskEntity(title = "x", dueDate = day.toEpochDay()), zone))
        assertNull(ReminderTime.triggerMillis(TaskEntity(title = "x", reminderEnabled = true), zone))
    }
}
