package com.personal.todo.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.personal.todo.domain.Priority
import java.util.UUID

@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["isCompleted", "dueDate"]),
        Index(value = ["completedAt"]),
        Index(value = ["uid"], unique = true),
    ],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable id used to detect duplicates when importing a backup. */
    val uid: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    /** Due date as epoch day (LocalDate.toEpochDay) or null. */
    val dueDate: Long? = null,
    /** Due time as minutes after midnight, or null. */
    val dueTime: Int? = null,
    val priority: Int = Priority.MEDIUM.value,
    val categoryId: Long? = null,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val reminderEnabled: Boolean = false,
    /** Minutes before the due moment to fire the reminder (0 = at due time). */
    val reminderOffsetMinutes: Int = 0,
    /** Name of [com.personal.todo.domain.Recurrence]. */
    val recurrence: String = "NONE",
    /** Used by CUSTOM recurrence: repeat every N days. */
    val recurrenceInterval: Int = 1,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "subtasks",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["taskId"])],
)
data class SubtaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val title: String,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val position: Int = 0,
)

data class TaskWithSubtasks(
    @Embedded val task: TaskEntity,
    @Relation(parentColumn = "id", entityColumn = "taskId") val subtasks: List<SubtaskEntity>,
)
