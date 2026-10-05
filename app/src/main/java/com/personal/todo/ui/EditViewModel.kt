package com.personal.todo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.todo.AppContainer
import com.personal.todo.data.CategoryEntity
import com.personal.todo.data.SubtaskEntity
import com.personal.todo.data.TaskEntity
import com.personal.todo.data.TaskWithSubtasks
import com.personal.todo.domain.NaturalDateParser
import com.personal.todo.domain.Priority
import com.personal.todo.domain.Recurrence
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class SubtaskDraft(val key: Long, val title: String, val done: Boolean)

data class EditForm(
    val title: String = "",
    val description: String = "",
    val date: LocalDate? = null,
    val minutes: Int? = null,
    val priority: Priority = Priority.MEDIUM,
    val categoryId: Long? = null,
    val reminderEnabled: Boolean = false,
    val reminderOffset: Int = 0,
    val recurrence: Recurrence = Recurrence.NONE,
    val interval: Int = 2,
    val completed: Boolean = false,
    val subtasks: List<SubtaskDraft> = emptyList(),
)

sealed interface SaveResult {
    data object Ok : SaveResult
    data class Error(val message: String) : SaveResult
}

class EditViewModel(container: AppContainer, private val taskId: Long) : ViewModel() {
    private val repo = container.repository
    private val settings = container.settings

    val isNew = taskId == 0L
    var form by mutableStateOf(EditForm())
        private set
    var loaded by mutableStateOf(isNew)
        private set
    var missing by mutableStateOf(false)
        private set
    var titleError by mutableStateOf(false)
        private set
    var createdAt by mutableStateOf<Long?>(null)
        private set
    var completedAt by mutableStateOf<Long?>(null)
        private set

    private var original: TaskWithSubtasks? = null
    private var nextKey = 1L

    val categories: StateFlow<List<CategoryEntity>> =
        repo.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (isNew) {
            val s = settings.settings.value
            form = EditForm(priority = s.defaultPriority, categoryId = s.defaultCategoryId.takeIf { it != 0L })
        } else {
            viewModelScope.launch {
                val existing = try { repo.getTaskWithSubtasks(taskId) } catch (e: Exception) { null }
                if (existing == null) {
                    missing = true
                } else {
                    original = existing
                    val t = existing.task
                    createdAt = t.createdAt
                    completedAt = t.completedAt
                    form = EditForm(
                        title = t.title, description = t.description,
                        date = t.dueDate?.let(LocalDate::ofEpochDay), minutes = t.dueTime,
                        priority = Priority.from(t.priority), categoryId = t.categoryId,
                        reminderEnabled = t.reminderEnabled, reminderOffset = t.reminderOffsetMinutes,
                        recurrence = Recurrence.from(t.recurrence), interval = t.recurrenceInterval,
                        completed = t.isCompleted,
                        subtasks = existing.subtasks.sortedBy { it.position }
                            .map { SubtaskDraft(nextKey++, it.title, it.isCompleted) },
                    )
                    loaded = true
                }
            }
        }
    }

    fun update(transform: (EditForm) -> EditForm) {
        form = transform(form)
        if (form.title.isNotBlank()) titleError = false
    }

    fun addSubtask(title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        form = form.copy(subtasks = form.subtasks + SubtaskDraft(nextKey++, clean, false))
    }

    fun updateSubtask(key: Long, transform: (SubtaskDraft) -> SubtaskDraft) {
        form = form.copy(subtasks = form.subtasks.map { if (it.key == key) transform(it) else it })
    }

    fun removeSubtask(key: Long) {
        form = form.copy(subtasks = form.subtasks.filterNot { it.key == key })
    }

    suspend fun addCategory(name: String) {
        val id = repo.addCategory(name) ?: return
        form = form.copy(categoryId = id)
    }

    suspend fun save(pendingSubtask: String): SaveResult {
        addSubtask(pendingSubtask)
        val f = form
        var title = f.title.trim()
        if (title.isEmpty()) {
            titleError = true
            return SaveResult.Error("Please enter a task title.")
        }
        var date = f.date
        var minutes = f.minutes
        // Quick add: "Call Pankaj tomorrow 10 AM" fills in the date and time when none were picked.
        if (isNew && date == null && minutes == null) {
            val parsed = NaturalDateParser.parse(title)
            title = parsed.title
            date = parsed.date
            minutes = parsed.minutes
        }
        if (minutes != null && date == null) {
            val now = LocalTime.now()
            date = if (minutes > now.hour * 60 + now.minute) LocalDate.now() else LocalDate.now().plusDays(1)
        }
        if (f.reminderEnabled && date == null) return SaveResult.Error("Set a due date to use a reminder.")
        if (f.recurrence != Recurrence.NONE && date == null) return SaveResult.Error("Set a due date for a repeating task.")

        return try {
            val base = original?.task
            val now = System.currentTimeMillis()
            val categoryId = f.categoryId?.takeIf { repo.categoryExists(it) }
            val task = TaskEntity(
                id = base?.id ?: 0,
                uid = base?.uid ?: UUID.randomUUID().toString(),
                title = title,
                description = f.description.trim(),
                dueDate = date?.toEpochDay(),
                dueTime = minutes,
                priority = f.priority.value,
                categoryId = categoryId,
                isCompleted = base?.isCompleted ?: false,
                createdAt = base?.createdAt ?: now,
                completedAt = base?.completedAt,
                reminderEnabled = f.reminderEnabled,
                reminderOffsetMinutes = f.reminderOffset,
                recurrence = f.recurrence.name,
                recurrenceInterval = f.interval.coerceIn(1, 365),
                updatedAt = now,
            )
            val subtasks = form.subtasks.mapIndexed { i, s ->
                SubtaskEntity(taskId = task.id, title = s.title, isCompleted = s.done, position = i)
            }
            val id = repo.save(task, subtasks)
            if (base != null && base.isCompleted != f.completed) {
                if (f.completed) repo.complete(id) else repo.reopen(id)
            }
            SaveResult.Ok
        } catch (e: Exception) {
            SaveResult.Error("Couldn't save the task. Please try again.")
        }
    }

    suspend fun delete(): Boolean = try {
        repo.delete(taskId) != null
    } catch (e: Exception) {
        false
    }
}
