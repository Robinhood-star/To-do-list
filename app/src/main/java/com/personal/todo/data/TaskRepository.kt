package com.personal.todo.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.personal.todo.domain.Recurrence
import com.personal.todo.domain.RecurrenceLogic
import com.personal.todo.domain.Retention
import com.personal.todo.reminders.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

data class CompletionResult(val spawnedId: Long?)

class TaskRepository(
    private val db: AppDatabase,
    private val reminders: ReminderScheduler,
) {
    private val taskDao = db.taskDao()
    private val subtaskDao = db.subtaskDao()
    private val categoryDao = db.categoryDao()

    val tasks: Flow<List<TaskWithSubtasks>> = taskDao.observeAll()
    val categories: Flow<List<CategoryEntity>> = categoryDao.observeAll()

    suspend fun getTask(id: Long): TaskEntity? = taskDao.getById(id)
    suspend fun getTaskWithSubtasks(id: Long): TaskWithSubtasks? = taskDao.getWithSubtasks(id)
    suspend fun categoryExists(id: Long): Boolean = categoryDao.getById(id) != null

    // ---------- tasks ----------

    /** Inserts (id == 0) or updates a task together with its subtasks. Returns the task id. */
    suspend fun save(task: TaskEntity, subtasks: List<SubtaskEntity>): Long {
        val now = System.currentTimeMillis()
        val stamped = task.copy(updatedAt = now)
        val id = db.withTransaction {
            val taskId = if (stamped.id == 0L) taskDao.insert(stamped) else {
                taskDao.update(stamped)
                stamped.id
            }
            subtaskDao.deleteForTask(taskId)
            subtaskDao.insertAll(subtasks.mapIndexed { i, s -> s.copy(id = 0, taskId = taskId, position = i) })
            taskId
        }
        taskDao.getById(id)?.let(reminders::schedule)
        return id
    }

    /** Marks a task completed. Recurring tasks spawn their next occurrence. */
    suspend fun complete(id: Long, today: LocalDate = LocalDate.now()): CompletionResult {
        var spawnedId: Long? = null
        db.withTransaction {
            val current = taskDao.getWithSubtasks(id) ?: return@withTransaction
            if (current.task.isCompleted) return@withTransaction
            val now = System.currentTimeMillis()
            taskDao.update(current.task.copy(isCompleted = true, completedAt = now, updatedAt = now))

            val recurrence = Recurrence.from(current.task.recurrence)
            val due = current.task.dueDate
            if (recurrence != Recurrence.NONE && due != null) {
                val next = RecurrenceLogic.nextOccurrence(
                    LocalDate.ofEpochDay(due), recurrence, current.task.recurrenceInterval, today
                )
                if (next != null) {
                    val copy = current.task.copy(
                        id = 0, uid = UUID.randomUUID().toString(), dueDate = next.toEpochDay(),
                        isCompleted = false, completedAt = null, createdAt = now, updatedAt = now,
                    )
                    val newId = taskDao.insert(copy)
                    subtaskDao.insertAll(current.subtasks.map { it.copy(id = 0, taskId = newId, isCompleted = false) })
                    spawnedId = newId
                }
            }
        }
        reminders.cancel(id)
        spawnedId?.let { sid -> taskDao.getById(sid)?.let(reminders::schedule) }
        return CompletionResult(spawnedId)
    }

    suspend fun reopen(id: Long) {
        val task = taskDao.getById(id) ?: return
        taskDao.update(task.copy(isCompleted = false, completedAt = null, updatedAt = System.currentTimeMillis()))
        taskDao.getById(id)?.let(reminders::schedule)
    }

    suspend fun undoComplete(id: Long, spawnedId: Long?) {
        if (spawnedId != null) {
            taskDao.deleteById(spawnedId)
            reminders.cancel(spawnedId)
        }
        reopen(id)
    }

    /** Deletes a task and returns a snapshot so the caller can offer Undo. */
    suspend fun delete(id: Long): TaskWithSubtasks? {
        val snapshot = taskDao.getWithSubtasks(id) ?: return null
        taskDao.deleteById(id)
        reminders.cancel(id)
        return snapshot
    }

    suspend fun restore(snapshot: TaskWithSubtasks) {
        db.withTransaction {
            taskDao.insert(snapshot.task)
            subtaskDao.insertAll(snapshot.subtasks)
        }
        reminders.schedule(snapshot.task)
    }

    // ---------- maintenance ----------

    /** Deletes completed tasks older than the retention period. Active tasks are never touched. */
    suspend fun cleanupExpired(retentionDays: Int, now: Long = System.currentTimeMillis()): Int =
        taskDao.deleteCompletedBefore(Retention.cutoffMillis(now, retentionDays))

    suspend fun clearCompleted(): Int = taskDao.deleteCompleted()

    suspend fun clearAll() {
        val ids = taskDao.allIds()
        db.withTransaction {
            taskDao.deleteAll()
            categoryDao.deleteAll()
            val now = System.currentTimeMillis()
            DEFAULT_CATEGORIES.forEach { categoryDao.insert(CategoryEntity(name = it, createdAt = now)) }
        }
        ids.forEach(reminders::cancel)
    }

    suspend fun rescheduleAll() {
        taskDao.activeWithReminders().forEach(reminders::schedule)
    }

    // ---------- categories ----------

    /** Adds a category (or returns the id of an existing one with the same name). Null if the name is blank. */
    suspend fun addCategory(name: String): Long? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        categoryDao.getByName(clean)?.let { return it.id }
        val id = categoryDao.insert(CategoryEntity(name = clean))
        return if (id > 0) id else categoryDao.getByName(clean)?.id
    }

    /** Returns false if the name is blank or already used. */
    suspend fun renameCategory(id: Long, name: String): Boolean {
        val clean = name.trim()
        if (clean.isEmpty()) return false
        val clash = categoryDao.getByName(clean)
        if (clash != null && clash.id != id) return false
        return try {
            categoryDao.rename(id, clean)
            true
        } catch (e: SQLiteConstraintException) {
            false
        }
    }

    suspend fun deleteCategory(id: Long) = categoryDao.delete(id)

    // ---------- backup ----------

    suspend fun exportBackup(): BackupData {
        val names = categoryDao.getAll()
        val byId = names.associate { it.id to it.name }
        val tasks = taskDao.getAllWithSubtasks().map { t ->
            BackupTask(
                uid = t.task.uid, title = t.task.title, description = t.task.description,
                dueDate = t.task.dueDate, dueTime = t.task.dueTime, priority = t.task.priority,
                categoryName = t.task.categoryId?.let(byId::get), isCompleted = t.task.isCompleted,
                createdAt = t.task.createdAt, completedAt = t.task.completedAt,
                reminderEnabled = t.task.reminderEnabled, reminderOffsetMinutes = t.task.reminderOffsetMinutes,
                recurrence = t.task.recurrence, recurrenceInterval = t.task.recurrenceInterval,
                updatedAt = t.task.updatedAt,
                subtasks = t.subtasks.sortedBy { it.position }.map { BackupSubtask(it.title, it.isCompleted, it.createdAt) },
            )
        }
        return BackupData(com.personal.todo.domain.AppConfig.BACKUP_VERSION, System.currentTimeMillis(), names.map { it.name }, tasks)
    }

    /**
     * Merges a backup into the database. Matching is by uid (or by content when the file has no uid):
     * new tasks are added, existing ones are replaced only if the backup copy was edited more recently.
     */
    suspend fun importBackup(data: BackupData): ImportResult {
        var added = 0
        var updated = 0
        var skipped = 0
        db.withTransaction {
            val catIds = HashMap<String, Long>()
            categoryDao.getAll().forEach { catIds[it.name.lowercase()] = it.id }
            suspend fun ensureCategory(name: String): Long = catIds.getOrPut(name.lowercase()) {
                val id = categoryDao.insert(CategoryEntity(name = name))
                if (id > 0) id else categoryDao.getByName(name)!!.id
            }
            data.categories.forEach { ensureCategory(it) }

            for (bt in data.tasks) {
                val catId = bt.categoryName?.let { ensureCategory(it) }
                val existing = if (bt.uid.isNotBlank()) taskDao.getByUid(bt.uid)
                else taskDao.findDuplicate(bt.title, bt.dueDate, bt.dueTime, bt.createdAt)
                val subs = bt.subtasks.mapIndexed { i, s ->
                    SubtaskEntity(taskId = 0, title = s.title, isCompleted = s.isCompleted, createdAt = s.createdAt, position = i)
                }
                fun entity(id: Long, uid: String) = TaskEntity(
                    id = id, uid = uid, title = bt.title, description = bt.description, dueDate = bt.dueDate,
                    dueTime = bt.dueTime, priority = bt.priority, categoryId = catId, isCompleted = bt.isCompleted,
                    createdAt = bt.createdAt, completedAt = bt.completedAt, reminderEnabled = bt.reminderEnabled,
                    reminderOffsetMinutes = bt.reminderOffsetMinutes, recurrence = bt.recurrence,
                    recurrenceInterval = bt.recurrenceInterval, updatedAt = bt.updatedAt,
                )
                when {
                    existing == null -> {
                        val id = taskDao.insert(entity(0, bt.uid.ifBlank { UUID.randomUUID().toString() }))
                        subtaskDao.insertAll(subs.map { it.copy(taskId = id) })
                        added++
                    }
                    ImportPolicy.shouldReplace(existing.updatedAt, bt.updatedAt) -> {
                        taskDao.update(entity(existing.id, existing.uid))
                        subtaskDao.deleteForTask(existing.id)
                        subtaskDao.insertAll(subs.map { it.copy(taskId = existing.id) })
                        updated++
                    }
                    else -> skipped++
                }
            }
        }
        rescheduleAll()
        return ImportResult(added, updated, skipped, data.invalidCount)
    }
}
