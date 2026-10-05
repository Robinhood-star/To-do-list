package com.personal.todo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.personal.todo.data.AppDatabase
import com.personal.todo.data.SubtaskEntity
import com.personal.todo.data.TaskEntity
import com.personal.todo.data.TaskRepository
import com.personal.todo.domain.Recurrence
import com.personal.todo.reminders.ReminderScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

private object NoReminders : ReminderScheduler {
    override fun schedule(task: TaskEntity) = Unit
    override fun cancel(taskId: Long) = Unit
}

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: TaskRepository
    private val today = LocalDate.of(2026, 10, 5)
    private val day = 86_400_000L

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = TaskRepository(db, NoReminders)
    }

    @After fun tearDown() = db.close()

    private suspend fun all() = repo.tasks.first()

    @Test fun createAndUpdate() = runBlocking {
        val id = repo.save(TaskEntity(title = "Write report"), listOf(SubtaskEntity(taskId = 0, title = "Outline")))
        var stored = repo.getTaskWithSubtasks(id)!!
        assertEquals("Write report", stored.task.title)
        assertEquals(1, stored.subtasks.size)

        repo.save(stored.task.copy(title = "Write final report"), listOf(SubtaskEntity(taskId = id, title = "A"), SubtaskEntity(taskId = id, title = "B")))
        stored = repo.getTaskWithSubtasks(id)!!
        assertEquals("Write final report", stored.task.title)
        assertEquals(listOf("A", "B"), stored.subtasks.sortedBy { it.position }.map { it.title })
    }

    @Test fun completeStoresTimestampAndUndoReverts() = runBlocking {
        val id = repo.save(TaskEntity(title = "Pay bill"), emptyList())
        val result = repo.complete(id)
        assertNull(result.spawnedId)
        val done = repo.getTask(id)!!
        assertTrue(done.isCompleted)
        assertNotNull(done.completedAt)

        repo.undoComplete(id, result.spawnedId)
        val reopened = repo.getTask(id)!!
        assertFalse(reopened.isCompleted)
        assertNull(reopened.completedAt)
    }

    @Test fun completingRecurringTaskSpawnsNextAndUndoRemovesIt() = runBlocking {
        val id = repo.save(
            TaskEntity(title = "Water", dueDate = today.toEpochDay(), recurrence = Recurrence.DAILY.name),
            listOf(SubtaskEntity(taskId = 0, title = "Fill bottle")),
        )
        val result = repo.complete(id, today)
        val next = repo.getTaskWithSubtasks(result.spawnedId!!)!!
        assertEquals(today.plusDays(1).toEpochDay(), next.task.dueDate)
        assertFalse(next.task.isCompleted)
        assertEquals(1, next.subtasks.size)
        assertEquals(2, all().size)

        repo.undoComplete(id, result.spawnedId)
        assertEquals(1, all().size)
    }

    @Test fun deleteAndRestoreKeepsSubtasks() = runBlocking {
        val id = repo.save(TaskEntity(title = "Trip"), listOf(SubtaskEntity(taskId = 0, title = "Book hotel")))
        val snapshot = repo.delete(id)!!
        assertTrue(all().isEmpty())
        repo.restore(snapshot)
        val back = repo.getTaskWithSubtasks(id)!!
        assertEquals("Trip", back.task.title)
        assertEquals(1, back.subtasks.size)
    }

    @Test fun cleanupRemovesOnlyOldCompletedTasks() = runBlocking {
        val now = 1_800_000_000_000L
        val oldDone = repo.save(TaskEntity(title = "old", isCompleted = true, completedAt = now - 31 * day), emptyList())
        val recentDone = repo.save(TaskEntity(title = "recent", isCompleted = true, completedAt = now - 5 * day), emptyList())
        val oldActive = repo.save(TaskEntity(title = "ancient but active", createdAt = now - 400 * day), emptyList())

        assertEquals(1, repo.cleanupExpired(30, now))
        assertNull(repo.getTask(oldDone))
        assertNotNull(repo.getTask(recentDone))
        assertNotNull(repo.getTask(oldActive))
    }

    @Test fun deletingCategoryKeepsTasks() = runBlocking {
        val cat = repo.addCategory("Gym")!!
        val id = repo.save(TaskEntity(title = "Run", categoryId = cat), emptyList())
        repo.deleteCategory(cat)
        assertNull(repo.getTask(id)!!.categoryId)
    }

    @Test fun exportThenImportIntoEmptyDatabaseRestoresTasks() = runBlocking {
        val cat = repo.addCategory("Work")!!
        repo.save(TaskEntity(title = "A", categoryId = cat, dueDate = today.toEpochDay()), listOf(SubtaskEntity(taskId = 0, title = "s1")))
        val backup = repo.exportBackup()

        val db2 = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val repo2 = TaskRepository(db2, NoReminders)
        val result = repo2.importBackup(backup)
        assertEquals(1, result.added)
        val restored = repo2.tasks.first().single()
        assertEquals("A", restored.task.title)
        assertEquals(1, restored.subtasks.size)

        // Importing the same file again must not create duplicates.
        val again = repo2.importBackup(backup)
        assertEquals(0, again.added)
        assertEquals(1, again.skipped)
        assertEquals(1, repo2.tasks.first().size)
        db2.close()
    }

    @Test fun clearAllResetsToDefaultCategories() = runBlocking {
        repo.save(TaskEntity(title = "x"), emptyList())
        repo.clearAll()
        assertTrue(all().isEmpty())
        assertEquals(6, repo.categories.first().size)
    }
}
