package com.personal.todo

import com.personal.todo.domain.NaturalDateParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class NaturalDateParserTest {
    private val now = LocalDateTime.of(2026, 10, 5, 8, 0) // Monday 08:00

    @Test fun tomorrowWithTime() {
        val r = NaturalDateParser.parse("Call Pankaj tomorrow 10 AM", now)
        assertEquals("Call Pankaj", r.title)
        assertEquals(LocalDate.of(2026, 10, 6), r.date)
        assertEquals(10 * 60, r.minutes)
    }

    @Test fun pmAndMinutes() {
        val r = NaturalDateParser.parse("Dentist today at 5:30 pm", now)
        assertEquals("Dentist", r.title)
        assertEquals(LocalDate.of(2026, 10, 5), r.date)
        assertEquals(17 * 60 + 30, r.minutes)
    }

    @Test fun twentyFourHourClock() {
        assertEquals(18 * 60, NaturalDateParser.parse("Gym 18:00", now).minutes)
    }

    @Test fun weekdayIsNextOccurrence() {
        val r = NaturalDateParser.parse("Team lunch on friday", now)
        assertEquals("Team lunch", r.title)
        assertEquals(LocalDate.of(2026, 10, 9), r.date)
    }

    @Test fun sameWeekdayMeansNextWeek() {
        assertEquals(LocalDate.of(2026, 10, 12), NaturalDateParser.parse("Review monday", now).date)
    }

    @Test fun inNDays() {
        assertEquals(LocalDate.of(2026, 10, 8), NaturalDateParser.parse("Pay rent in 3 days", now).date)
        assertEquals(LocalDate.of(2026, 10, 19), NaturalDateParser.parse("Follow up in 2 weeks", now).date)
    }

    @Test fun timeOnly_pastTimeRollsToTomorrow() {
        assertEquals(LocalDate.of(2026, 10, 5), NaturalDateParser.parse("Call at 9pm", now).date)
        assertEquals(LocalDate.of(2026, 10, 6), NaturalDateParser.parse("Call at 7am", now).date)
    }

    @Test fun plainTitleIsUntouched() {
        val r = NaturalDateParser.parse("  Buy milk  ", now)
        assertEquals("Buy milk", r.title)
        assertNull(r.date)
        assertNull(r.minutes)
    }

    @Test fun titleThatIsOnlyADateKeepsOriginalText() {
        assertEquals("tomorrow", NaturalDateParser.parse("tomorrow", now).title)
    }

    @Test fun invalidClockIsIgnored() {
        assertNull(NaturalDateParser.parse("Meet at 13pm", now).minutes)
    }
}
