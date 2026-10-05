package com.personal.todo.domain

import com.personal.todo.data.TaskEntity
import java.time.LocalDate
import java.time.ZoneId

object ReminderTime {
    /** Epoch millis at which this task's reminder should fire, or null if it has none. */
    fun triggerMillis(task: TaskEntity, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (!task.reminderEnabled) return null
        val day = task.dueDate ?: return null
        val minutes = task.dueTime ?: AppConfig.DEFAULT_REMINDER_MINUTES
        val moment = LocalDate.ofEpochDay(day).atStartOfDay()
            .plusMinutes(minutes.toLong())
            .minusMinutes(task.reminderOffsetMinutes.coerceAtLeast(0).toLong())
        return moment.atZone(zone).toInstant().toEpochMilli()
    }
}
