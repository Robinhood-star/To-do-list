package com.personal.todo.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

data class ParsedTask(val title: String, val date: LocalDate?, val minutes: Int?)

/**
 * Tiny offline parser for phrases like "Call Pankaj tomorrow 10 AM".
 * Supports: today, tomorrow, in N days/weeks, weekday names, "10am", "10:30 pm", "18:00".
 * Anything it does not understand is left in the title.
 */
object NaturalDateParser {
    private val opt = setOf(RegexOption.IGNORE_CASE)
    private val time12 = Regex("""\b(?:at\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b""", opt)
    private val time24 = Regex("""\b(?:at\s+)?([01]?\d|2[0-3]):([0-5]\d)\b""", opt)
    private val today = Regex("""\b(?:on\s+)?today\b""", opt)
    private val tomorrow = Regex("""\b(?:on\s+)?(?:tomorrow|tmrw)\b""", opt)
    private val inN = Regex("""\bin\s+(\d{1,3})\s+(day|days|week|weeks)\b""", opt)
    private val weekday = Regex(
        """\b(?:(next|on|this)\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""", opt
    )
    private val trailing = Regex("""\s+(on|at|by|for|due|from)\s*$""", opt)

    fun parse(input: String, now: LocalDateTime = LocalDateTime.now()): ParsedTask {
        val original = input.trim()
        var text = original
        var date: LocalDate? = null
        var minutes: Int? = null
        val todayDate = now.toLocalDate()

        // Time
        time12.find(text)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].ifEmpty { "0" }.toInt()
            if (h in 1..12 && min in 0..59) {
                val pm = m.groupValues[3].equals("pm", ignoreCase = true)
                minutes = ((h % 12) + if (pm) 12 else 0) * 60 + min
                text = text.removeRange(m.range)
            }
        }
        if (minutes == null) {
            time24.find(text)?.let { m ->
                minutes = m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
                text = text.removeRange(m.range)
            }
        }

        // Date
        tomorrow.find(text)?.let { m -> date = todayDate.plusDays(1); text = text.removeRange(m.range) }
        if (date == null) today.find(text)?.let { m -> date = todayDate; text = text.removeRange(m.range) }
        if (date == null) inN.find(text)?.let { m ->
            val n = m.groupValues[1].toLong()
            val weeks = m.groupValues[2].lowercase().startsWith("week")
            date = todayDate.plusDays(if (weeks) n * 7 else n)
            text = text.removeRange(m.range)
        }
        if (date == null) weekday.find(text)?.let { m ->
            val dow = DayOfWeek.valueOf(m.groupValues[2].uppercase())
            val prefix = m.groupValues[1].lowercase()
            date = if (prefix == "this") todayDate.with(TemporalAdjusters.nextOrSame(dow))
            else todayDate.with(TemporalAdjusters.next(dow))
            text = text.removeRange(m.range)
        }

        // A time with no date means "today", or tomorrow if that time already passed.
        val mins = minutes
        if (date == null && mins != null) {
            val nowMin = now.hour * 60 + now.minute
            date = if (mins > nowMin) todayDate else todayDate.plusDays(1)
        }

        if (date == null && minutes == null) return ParsedTask(original, null, null)

        var title = text.replace(Regex("""\s+"""), " ").trim()
        title = title.replace(trailing, "").trim()
        if (title.isBlank()) title = original
        return ParsedTask(title, date, minutes)
    }
}
