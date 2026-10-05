package com.personal.todo.domain

object AppConfig {
    /** Completed tasks are removed after this many days. Single place to change the default. */
    const val DEFAULT_RETENTION_DAYS = 30
    val RETENTION_OPTIONS = listOf(7, 14, 30, 60, 90)
    const val BACKUP_VERSION = 1
    /** Reminder time (minutes after midnight) for tasks that have a date but no time. */
    const val DEFAULT_REMINDER_MINUTES = 9 * 60
}

enum class Priority(val value: Int, val label: String) {
    LOW(0, "Low"), MEDIUM(1, "Medium"), HIGH(2, "High"), URGENT(3, "Urgent");

    companion object {
        fun from(value: Int): Priority = entries.firstOrNull { it.value == value } ?: MEDIUM
    }
}

enum class Recurrence(val label: String) {
    NONE("Never"), DAILY("Daily"), WEEKDAYS("Weekdays"), WEEKLY("Weekly"), MONTHLY("Monthly"), CUSTOM("Custom");

    companion object {
        fun from(name: String?): Recurrence = entries.firstOrNull { it.name == name } ?: NONE
    }
}

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

enum class SortType(val label: String) { DUE("Due time"), PRIORITY("Priority"), CREATED("Newest") }

enum class FilterType(val label: String) {
    ALL("All"), TODAY("Today"), UPCOMING("Upcoming"), OVERDUE("Overdue"), COMPLETED("Completed"), HIGH("High priority")
}

enum class Section(val title: String) {
    OVERDUE("Overdue"), TODAY("Today"), UPCOMING("Upcoming"), NO_DATE("No date"), COMPLETED("Completed")
}

object Retention {
    private const val DAY_MS = 86_400_000L

    fun cutoffMillis(now: Long, days: Int): Long = now - days.coerceAtLeast(1) * DAY_MS

    fun isExpired(completedAt: Long?, now: Long, days: Int): Boolean =
        completedAt != null && completedAt < cutoffMillis(now, days)
}
