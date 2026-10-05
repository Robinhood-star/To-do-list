package com.personal.todo.domain

import java.time.DayOfWeek
import java.time.LocalDate

object RecurrenceLogic {

    /** The occurrence directly after [date]. */
    fun nextAfter(date: LocalDate, recurrence: Recurrence, interval: Int): LocalDate? = when (recurrence) {
        Recurrence.NONE -> null
        Recurrence.DAILY -> date.plusDays(1)
        Recurrence.WEEKDAYS -> {
            var d = date.plusDays(1)
            while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) d = d.plusDays(1)
            d
        }
        Recurrence.WEEKLY -> date.plusWeeks(1)
        Recurrence.MONTHLY -> date.plusMonths(1)
        Recurrence.CUSTOM -> date.plusDays(interval.coerceAtLeast(1).toLong())
    }

    /** Next due date after completing a task; never earlier than [today] (so overdue tasks don't pile up). */
    fun nextOccurrence(due: LocalDate, recurrence: Recurrence, interval: Int, today: LocalDate): LocalDate? {
        var next = nextAfter(due, recurrence, interval) ?: return null
        var guard = 0
        while (next.isBefore(today) && guard++ < 2000) {
            next = nextAfter(next, recurrence, interval) ?: return null
        }
        return next
    }
}
