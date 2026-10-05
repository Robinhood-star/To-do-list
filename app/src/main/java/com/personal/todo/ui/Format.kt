package com.personal.todo.ui

import com.personal.todo.data.TaskEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

fun formatMinutes(minutes: Int): String = timeFormatter.format(LocalTime.of(minutes / 60, minutes % 60))

fun formatDay(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(
        DateTimeFormatter.ofPattern(if (date.year == today.year) "EEE, d MMM" else "d MMM yyyy", Locale.getDefault())
    )
}

fun formatDue(task: TaskEntity, today: LocalDate): String? {
    val date = task.dueDate?.let(LocalDate::ofEpochDay) ?: return null
    val day = formatDay(date, today)
    return task.dueTime?.let { "$day, ${formatMinutes(it)}" } ?: day
}

fun isOverdue(task: TaskEntity, today: LocalDate, now: LocalTime = LocalTime.now()): Boolean {
    if (task.isCompleted) return false
    val due = task.dueDate ?: return false
    val t = today.toEpochDay()
    if (due < t) return true
    val time = task.dueTime
    return due == t && time != null && time < now.hour * 60 + now.minute
}
