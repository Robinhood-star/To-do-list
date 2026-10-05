package com.personal.todo.data

import com.personal.todo.domain.AppConfig
import com.personal.todo.domain.Priority
import com.personal.todo.domain.Recurrence
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class BackupException(message: String) : Exception(message)

data class BackupSubtask(val title: String, val isCompleted: Boolean, val createdAt: Long)

data class BackupTask(
    val uid: String,
    val title: String,
    val description: String,
    val dueDate: Long?,
    val dueTime: Int?,
    val priority: Int,
    val categoryName: String?,
    val isCompleted: Boolean,
    val createdAt: Long,
    val completedAt: Long?,
    val reminderEnabled: Boolean,
    val reminderOffsetMinutes: Int,
    val recurrence: String,
    val recurrenceInterval: Int,
    val updatedAt: Long,
    val subtasks: List<BackupSubtask>,
)

data class BackupData(
    val version: Int,
    val exportedAt: Long,
    val categories: List<String>,
    val tasks: List<BackupTask>,
    /** Number of task entries in the file that were unusable (e.g. no title). */
    val invalidCount: Int = 0,
)

data class ImportResult(val added: Int, val updated: Int, val skipped: Int, val invalid: Int)

object ImportPolicy {
    /** On a uid conflict the newer edit wins. */
    fun shouldReplace(existingUpdatedAt: Long, incomingUpdatedAt: Long) = incomingUpdatedAt > existingUpdatedAt
}

object BackupJson {

    fun toJson(data: BackupData): String {
        val root = JSONObject()
        root.put("app", "PersonalTodo")
        root.put("version", data.version)
        root.put("exportedAt", data.exportedAt)
        root.put("categories", JSONArray(data.categories))
        val tasks = JSONArray()
        data.tasks.forEach { tasks.put(taskToJson(it)) }
        root.put("tasks", tasks)
        return root.toString(2)
    }

    private fun taskToJson(t: BackupTask): JSONObject {
        val o = JSONObject()
        o.put("uid", t.uid)
        o.put("title", t.title)
        o.put("description", t.description)
        o.put("dueDate", t.dueDate ?: JSONObject.NULL)
        o.put("dueTime", t.dueTime ?: JSONObject.NULL)
        o.put("priority", t.priority)
        o.put("category", t.categoryName ?: JSONObject.NULL)
        o.put("isCompleted", t.isCompleted)
        o.put("createdAt", t.createdAt)
        o.put("completedAt", t.completedAt ?: JSONObject.NULL)
        o.put("reminderEnabled", t.reminderEnabled)
        o.put("reminderOffsetMinutes", t.reminderOffsetMinutes)
        o.put("recurrence", t.recurrence)
        o.put("recurrenceInterval", t.recurrenceInterval)
        o.put("updatedAt", t.updatedAt)
        val subs = JSONArray()
        t.subtasks.forEach {
            subs.put(
                JSONObject()
                    .put("title", it.title)
                    .put("isCompleted", it.isCompleted)
                    .put("createdAt", it.createdAt)
            )
        }
        o.put("subtasks", subs)
        return o
    }

    private fun JSONObject.longOrNull(key: String): Long? = if (has(key) && !isNull(key)) optLong(key) else null
    private fun JSONObject.intOrNull(key: String): Int? = if (has(key) && !isNull(key)) optInt(key) else null
    private fun JSONObject.stringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).trim().ifEmpty { null } else null

    /** @throws BackupException with a user-friendly message if the text is not a usable backup. */
    fun parse(text: String): BackupData {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw BackupException("This file is not a valid backup (it is not valid JSON).")
        }
        val version = root.optInt("version", -1)
        if (version < 1) throw BackupException("This file does not look like a My Tasks backup.")
        if (version > AppConfig.BACKUP_VERSION) {
            throw BackupException("This backup was made by a newer version of the app. Please update the app first.")
        }
        val arr = root.optJSONArray("tasks") ?: throw BackupException("The backup does not contain a task list.")

        val categories = mutableListOf<String>()
        root.optJSONArray("categories")?.let { c ->
            for (i in 0 until c.length()) c.optString(i, "").trim().takeIf { it.isNotEmpty() }?.let(categories::add)
        }

        val now = System.currentTimeMillis()
        val tasks = mutableListOf<BackupTask>()
        var invalid = 0
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i)
            val title = o?.stringOrNull("title")
            if (o == null || title == null) {
                invalid++
                continue
            }
            val subs = mutableListOf<BackupSubtask>()
            o.optJSONArray("subtasks")?.let { s ->
                for (j in 0 until s.length()) {
                    val so = s.optJSONObject(j) ?: continue
                    val st = so.stringOrNull("title") ?: continue
                    subs += BackupSubtask(st, so.optBoolean("isCompleted", false), so.longOrNull("createdAt") ?: now)
                }
            }
            val created = o.longOrNull("createdAt") ?: now
            val completed = o.optBoolean("isCompleted", false)
            tasks += BackupTask(
                uid = o.stringOrNull("uid") ?: "",
                title = title,
                description = o.optString("description", ""),
                dueDate = o.longOrNull("dueDate"),
                dueTime = o.intOrNull("dueTime")?.takeIf { it in 0..1439 },
                priority = o.optInt("priority", Priority.MEDIUM.value).coerceIn(0, 3),
                categoryName = o.stringOrNull("category"),
                isCompleted = completed,
                createdAt = created,
                completedAt = if (completed) (o.longOrNull("completedAt") ?: created) else null,
                reminderEnabled = o.optBoolean("reminderEnabled", false),
                reminderOffsetMinutes = o.optInt("reminderOffsetMinutes", 0).coerceAtLeast(0),
                recurrence = Recurrence.from(o.stringOrNull("recurrence")).name,
                recurrenceInterval = o.optInt("recurrenceInterval", 1).coerceIn(1, 365),
                updatedAt = o.longOrNull("updatedAt") ?: created,
                subtasks = subs,
            )
        }
        return BackupData(version, root.optLong("exportedAt", now), categories, tasks, invalid)
    }
}
