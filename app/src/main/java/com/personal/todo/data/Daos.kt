package com.personal.todo.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Transaction @Query("SELECT * FROM tasks")
    fun observeAll(): Flow<List<TaskWithSubtasks>>

    @Transaction @Query("SELECT * FROM tasks")
    suspend fun getAllWithSubtasks(): List<TaskWithSubtasks>

    @Transaction @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getWithSubtasks(id: Long): TaskWithSubtasks?

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE uid = :uid LIMIT 1")
    suspend fun getByUid(uid: String): TaskEntity?

    @Query(
        "SELECT * FROM tasks WHERE title = :title AND dueDate IS :dueDate AND dueTime IS :dueTime " +
            "AND createdAt = :createdAt LIMIT 1"
    )
    suspend fun findDuplicate(title: String, dueDate: Long?, dueTime: Int?, createdAt: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND reminderEnabled = 1 AND dueDate IS NOT NULL")
    suspend fun activeWithReminders(): List<TaskEntity>

    @Query("SELECT id FROM tasks")
    suspend fun allIds(): List<Long>

    @Insert suspend fun insert(task: TaskEntity): Long

    @Update suspend fun update(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM tasks WHERE isCompleted = 1 AND completedAt IS NOT NULL AND completedAt < :cutoff")
    suspend fun deleteCompletedBefore(cutoff: Long): Int

    @Query("DELETE FROM tasks WHERE isCompleted = 1")
    suspend fun deleteCompleted(): Int

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()
}

@Dao
interface SubtaskDao {
    @Insert suspend fun insertAll(items: List<SubtaskEntity>)

    @Query("DELETE FROM subtasks WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: Long)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY id")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getByName(name: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: CategoryEntity): Long

    @Query("UPDATE categories SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}
