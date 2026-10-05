package com.personal.todo

import com.personal.todo.data.CategoryEntity
import com.personal.todo.data.SubtaskEntity
import com.personal.todo.data.TaskEntity
import com.personal.todo.data.TaskWithSubtasks
import com.personal.todo.domain.FilterType
import com.personal.todo.domain.Priority
import com.personal.todo.domain.Section
import com.personal.todo.domain.SortType
import com.personal.todo.domain.TaskLogic
import com.personal.todo.domain.TaskQuery
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TaskLogicTest {
    private val today = LocalDate.of(2026, 10, 5)
    private val cats = listOf(CategoryEntity(1, "Work"), CategoryEntity(2, "Shopping"))

    private fun t(
        id: Long, title: String, dayOffset: Int? = null, time: Int? = null, priority: Priority = Priority.MEDIUM,
        done: Boolean = false, cat: Long? = null, notes: String = "", created: Long = id, completedAt: Long? = null,
    ) = TaskWithSubtasks(
        TaskEntity(
            id = id, uid = "u$id", title = title, description = notes,
            dueDate = dayOffset?.let { today.plusDays(it.toLong()).toEpochDay() }, dueTime = time,
            priority = priority.value, categoryId = cat, isCompleted = done, createdAt = created,
            completedAt = if (done) (completedAt ?: id) else null,
        ),
        emptyList(),
    )

    private val items = listOf(
        t(1, "Pay electricity bill", -2, cat = 1),
        t(2, "Call Pankaj", 0, time = 600, priority = Priority.HIGH, cat = 1),
        t(3, "Buy milk", 0, time = 540, cat = 2, notes = "organic"),
        t(4, "Plan trip", 5, priority = Priority.URGENT),
        t(5, "Someday idea"),
        t(6, "Old finished", -1, done = true, completedAt = 100),
        t(7, "Recent finished", 0, done = true, completedAt = 200),
    )

    private fun build(q: TaskQuery = TaskQuery(), sort: SortType = SortType.DUE) = TaskLogic.build(items, cats, q, sort, today)
    private fun ids(section: Section, q: TaskQuery = TaskQuery(), sort: SortType = SortType.DUE) =
        build(q, sort).first { it.section == section }.rows.map { it.task.id }

    @Test fun sectionsAreAssigned() {
        val groups = build()
        assertEquals(
            listOf(Section.OVERDUE, Section.TODAY, Section.UPCOMING, Section.NO_DATE, Section.COMPLETED),
            groups.map { it.section },
        )
    }

    @Test fun todaySortedByTime() = assertEquals(listOf(3L, 2L), ids(Section.TODAY))
    @Test fun todaySortedByPriority() = assertEquals(listOf(2L, 3L), ids(Section.TODAY, sort = SortType.PRIORITY))
    @Test fun completedNewestFirst() = assertEquals(listOf(7L, 6L), ids(Section.COMPLETED))

    @Test fun filterToday() {
        val groups = build(TaskQuery(filter = FilterType.TODAY))
        assertEquals(listOf(Section.TODAY), groups.map { it.section })
    }

    @Test fun filterOverdueUpcomingCompleted() {
        assertEquals(listOf(1L), build(TaskQuery(filter = FilterType.OVERDUE)).flatMap { it.rows }.map { it.task.id })
        assertEquals(listOf(4L), build(TaskQuery(filter = FilterType.UPCOMING)).flatMap { it.rows }.map { it.task.id })
        assertEquals(setOf(6L, 7L), build(TaskQuery(filter = FilterType.COMPLETED)).flatMap { it.rows }.map { it.task.id }.toSet())
    }

    @Test fun filterHighPriorityIncludesUrgentButNotCompleted() {
        val result = build(TaskQuery(filter = FilterType.HIGH)).flatMap { it.rows }.map { it.task.id }.toSet()
        assertEquals(setOf(2L, 4L), result)
    }

    @Test fun filterByCategory() {
        val result = build(TaskQuery(categoryId = 1)).flatMap { it.rows }.map { it.task.id }.toSet()
        assertEquals(setOf(1L, 2L), result)
    }

    @Test fun filterBySelectedDate() {
        val q = TaskQuery(date = today)
        assertEquals(setOf(2L, 3L, 7L), build(q).flatMap { it.rows }.map { it.task.id }.toSet())
    }

    @Test fun searchTitleDescriptionAndCategory() {
        fun found(text: String) = build(TaskQuery(search = text)).flatMap { it.rows }.map { it.task.id }.toSet()
        assertEquals(setOf(2L), found("pankaj"))
        assertEquals(setOf(3L), found("ORGANIC"))
        assertEquals(setOf(3L), found("shopping"))
        assertEquals(emptySet<Long>(), found("zzz"))
    }

    @Test fun summaryCountsTodayDoneRemainingOverdue() {
        val s = TaskLogic.summary(items, today)
        assertEquals(3, s.todayTotal)
        assertEquals(1, s.todayDone)
        assertEquals(2, s.todayRemaining)
        assertEquals(1, s.overdue)
    }

    @Test fun subtaskProgressIsReported() {
        val withSubs = TaskWithSubtasks(
            TaskEntity(id = 9, title = "Prepare presentation"),
            listOf(
                SubtaskEntity(1, 9, "Collect data", true), SubtaskEntity(2, 9, "Create slides", true),
                SubtaskEntity(3, 9, "Review", false), SubtaskEntity(4, 9, "Send", false),
            ),
        )
        val row = TaskLogic.build(listOf(withSubs), cats, TaskQuery(), SortType.DUE, today).single().rows.single()
        assertEquals(2, row.subtasksDone)
        assertEquals(4, row.subtasksTotal)
    }
}
