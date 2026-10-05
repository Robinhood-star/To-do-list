package com.personal.todo.domain

import com.personal.todo.data.CategoryEntity
import com.personal.todo.data.TaskEntity
import com.personal.todo.data.TaskWithSubtasks
import java.time.LocalDate

data class TaskRow(
    val task: TaskEntity,
    val categoryName: String?,
    val subtasksDone: Int,
    val subtasksTotal: Int,
)

data class SectionGroup(val section: Section, val rows: List<TaskRow>)

data class TaskQuery(
    val filter: FilterType = FilterType.ALL,
    val categoryId: Long? = null,
    val search: String = "",
    val date: LocalDate? = null,
)

data class HeaderSummary(val todayTotal: Int, val todayDone: Int, val todayRemaining: Int, val overdue: Int)

/** Pure functions for sectioning, filtering, searching and sorting. Easy to unit test. */
object TaskLogic {

    fun sectionOf(task: TaskEntity, today: LocalDate): Section {
        if (task.isCompleted) return Section.COMPLETED
        val due = task.dueDate ?: return Section.NO_DATE
        val t = today.toEpochDay()
        return when {
            due < t -> Section.OVERDUE
            due == t -> Section.TODAY
            else -> Section.UPCOMING
        }
    }

    fun matchesFilter(task: TaskEntity, query: TaskQuery, today: LocalDate): Boolean {
        query.date?.let { return task.dueDate == it.toEpochDay() }
        val section = sectionOf(task, today)
        return when (query.filter) {
            FilterType.ALL -> true
            FilterType.TODAY -> section == Section.TODAY
            FilterType.UPCOMING -> section == Section.UPCOMING
            FilterType.OVERDUE -> section == Section.OVERDUE
            FilterType.COMPLETED -> section == Section.COMPLETED
            FilterType.HIGH -> !task.isCompleted && task.priority >= Priority.HIGH.value
        }
    }

    fun matchesSearch(row: TaskRow, needle: String): Boolean {
        if (needle.isEmpty()) return true
        return row.task.title.lowercase().contains(needle) ||
            row.task.description.lowercase().contains(needle) ||
            (row.categoryName?.lowercase()?.contains(needle) == true)
    }

    fun build(
        items: List<TaskWithSubtasks>,
        categories: List<CategoryEntity>,
        query: TaskQuery,
        sort: SortType,
        today: LocalDate,
    ): List<SectionGroup> {
        val names = categories.associate { it.id to it.name }
        val needle = query.search.trim().lowercase()
        val rows = items.asSequence()
            .map { TaskRow(it.task, it.task.categoryId?.let(names::get), it.subtasks.count { s -> s.isCompleted }, it.subtasks.size) }
            .filter { matchesFilter(it.task, query, today) }
            .filter { query.categoryId == null || it.task.categoryId == query.categoryId }
            .filter { matchesSearch(it, needle) }
            .toList()
        return Section.entries.mapNotNull { section ->
            val inSection = rows.filter { sectionOf(it.task, today) == section }
            if (inSection.isEmpty()) null else SectionGroup(section, inSection.sortedWith(comparator(section, sort)))
        }
    }

    fun comparator(section: Section, sort: SortType): Comparator<TaskRow> {
        if (section == Section.COMPLETED) return compareByDescending { it.task.completedAt ?: 0L }
        return when (sort) {
            SortType.DUE -> compareBy<TaskRow>(
                { it.task.dueDate ?: Long.MAX_VALUE },
                { it.task.dueTime ?: Int.MAX_VALUE },
                { -it.task.priority },
                { it.task.createdAt },
            )
            SortType.PRIORITY -> compareBy<TaskRow>(
                { -it.task.priority },
                { it.task.dueDate ?: Long.MAX_VALUE },
                { it.task.dueTime ?: Int.MAX_VALUE },
            )
            SortType.CREATED -> compareByDescending { it.task.createdAt }
        }
    }

    fun summary(items: List<TaskWithSubtasks>, today: LocalDate): HeaderSummary {
        val t = today.toEpochDay()
        val dueToday = items.map { it.task }.filter { it.dueDate == t }
        val done = dueToday.count { it.isCompleted }
        val overdue = items.count { !it.task.isCompleted && (it.task.dueDate ?: Long.MAX_VALUE) < t }
        return HeaderSummary(dueToday.size, done, dueToday.size - done, overdue)
    }
}
