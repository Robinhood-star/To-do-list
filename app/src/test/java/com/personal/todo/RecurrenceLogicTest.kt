package com.personal.todo

import com.personal.todo.domain.Recurrence
import com.personal.todo.domain.RecurrenceLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RecurrenceLogicTest {
    private val monday = LocalDate.of(2026, 10, 5)

    @Test fun none_hasNoNext() = assertNull(RecurrenceLogic.nextAfter(monday, Recurrence.NONE, 1))
    @Test fun daily() = assertEquals(monday.plusDays(1), RecurrenceLogic.nextAfter(monday, Recurrence.DAILY, 1))
    @Test fun weekly() = assertEquals(monday.plusWeeks(1), RecurrenceLogic.nextAfter(monday, Recurrence.WEEKLY, 1))
    @Test fun monthly_clampsToMonthEnd() =
        assertEquals(LocalDate.of(2026, 2, 28), RecurrenceLogic.nextAfter(LocalDate.of(2026, 1, 31), Recurrence.MONTHLY, 1))
    @Test fun custom_everyThreeDays() = assertEquals(monday.plusDays(3), RecurrenceLogic.nextAfter(monday, Recurrence.CUSTOM, 3))
    @Test fun custom_intervalBelowOneIsTreatedAsOne() = assertEquals(monday.plusDays(1), RecurrenceLogic.nextAfter(monday, Recurrence.CUSTOM, 0))

    @Test fun weekdays_skipsWeekend() {
        val friday = LocalDate.of(2026, 10, 9)
        assertEquals(LocalDate.of(2026, 10, 12), RecurrenceLogic.nextAfter(friday, Recurrence.WEEKDAYS, 1))
        assertEquals(LocalDate.of(2026, 10, 6), RecurrenceLogic.nextAfter(monday, Recurrence.WEEKDAYS, 1))
    }

    @Test fun overdueDaily_catchesUpToToday() {
        val due = monday.minusDays(5)
        assertEquals(monday, RecurrenceLogic.nextOccurrence(due, Recurrence.DAILY, 1, monday))
    }

    @Test fun completingOnDueDay_givesTomorrow() =
        assertEquals(monday.plusDays(1), RecurrenceLogic.nextOccurrence(monday, Recurrence.DAILY, 1, monday))

    @Test fun overdueWeekly_staysOnSameWeekday() {
        val due = monday.minusWeeks(3) // a Monday
        assertEquals(monday, RecurrenceLogic.nextOccurrence(due, Recurrence.WEEKLY, 1, monday))
    }
}
