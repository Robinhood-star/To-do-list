package com.personal.todo

import com.personal.todo.data.BackupData
import com.personal.todo.data.BackupException
import com.personal.todo.data.BackupJson
import com.personal.todo.data.BackupSubtask
import com.personal.todo.data.BackupTask
import com.personal.todo.data.ImportPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupJsonTest {
    private val task = BackupTask(
        uid = "abc", title = "Prepare presentation", description = "for Monday", dueDate = 20000, dueTime = 600,
        priority = 3, categoryName = "Work", isCompleted = false, createdAt = 1000, completedAt = null,
        reminderEnabled = true, reminderOffsetMinutes = 15, recurrence = "WEEKLY", recurrenceInterval = 1,
        updatedAt = 2000,
        subtasks = listOf(BackupSubtask("Collect data", true, 1), BackupSubtask("Review", false, 2)),
    )

    @Test fun roundTripKeepsEverything() {
        val data = BackupData(1, 5000, listOf("Work", "Personal"), listOf(task, task.copy(uid = "def", dueDate = null, dueTime = null, categoryName = null)))
        val parsed = BackupJson.parse(BackupJson.toJson(data))
        assertEquals(data.categories, parsed.categories)
        assertEquals(data.tasks, parsed.tasks)
        assertNull(parsed.tasks[1].dueDate)
        assertEquals(0, parsed.invalidCount)
    }

    @Test fun invalidJsonGivesFriendlyError() {
        try {
            BackupJson.parse("this is not json")
            fail("expected BackupException")
        } catch (e: BackupException) {
            assertTrue(e.message!!.isNotBlank())
        }
    }

    @Test(expected = BackupException::class) fun missingTasksArrayIsRejected() {
        BackupJson.parse("""{"version":1}""")
    }

    @Test(expected = BackupException::class) fun newerVersionIsRejected() {
        BackupJson.parse("""{"version":99,"tasks":[]}""")
    }

    @Test fun entriesWithoutTitleAreCountedAndSkipped() {
        val parsed = BackupJson.parse("""{"version":1,"tasks":[{"title":"  "},{"title":"Ok"},"junk"]}""")
        assertEquals(1, parsed.tasks.size)
        assertEquals(2, parsed.invalidCount)
        assertEquals("", parsed.tasks[0].uid)
    }

    @Test fun outOfRangeValuesAreClamped() {
        val parsed = BackupJson.parse("""{"version":1,"tasks":[{"title":"x","priority":99,"dueTime":5000,"recurrence":"BOGUS","recurrenceInterval":0}]}""")
        val t = parsed.tasks.single()
        assertEquals(3, t.priority)
        assertNull(t.dueTime)
        assertEquals("NONE", t.recurrence)
        assertEquals(1, t.recurrenceInterval)
    }

    @Test fun newerEditWinsOnConflict() {
        assertTrue(ImportPolicy.shouldReplace(existingUpdatedAt = 1, incomingUpdatedAt = 2))
        assertFalse(ImportPolicy.shouldReplace(existingUpdatedAt = 2, incomingUpdatedAt = 2))
        assertFalse(ImportPolicy.shouldReplace(existingUpdatedAt = 3, incomingUpdatedAt = 2))
    }
}
