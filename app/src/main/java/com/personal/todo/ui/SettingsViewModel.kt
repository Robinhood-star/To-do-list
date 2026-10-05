package com.personal.todo.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.todo.AppContainer
import com.personal.todo.data.AppSettings
import com.personal.todo.data.BackupException
import com.personal.todo.data.BackupJson
import com.personal.todo.data.CategoryEntity
import com.personal.todo.domain.Priority
import com.personal.todo.domain.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.repository
    private val store = container.settings

    val settings: StateFlow<AppSettings> = store.settings
    val categories: StateFlow<List<CategoryEntity>> =
        repo.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val messages = Channel<String>(Channel.BUFFERED)
    val events: Flow<String> = messages.receiveAsFlow()

    fun setTheme(mode: ThemeMode) = store.update { it.copy(theme = mode) }
    fun setRetention(days: Int) = store.update { it.copy(retentionDays = days) }
    fun setDefaultPriority(p: Priority) = store.update { it.copy(defaultPriority = p) }
    fun setDefaultCategory(id: Long) = store.update { it.copy(defaultCategoryId = id) }

    fun setRemindersEnabled(enabled: Boolean) {
        store.update { it.copy(remindersEnabled = enabled) }
        launchSafely { repo.rescheduleAll() }
    }

    fun addCategory(name: String) = launchSafely {
        if (repo.addCategory(name) == null) messages.send("Please enter a category name.")
    }

    fun renameCategory(id: Long, name: String) = launchSafely {
        if (!repo.renameCategory(id, name)) messages.send("That name is empty or already used.")
    }

    fun deleteCategory(id: Long) = launchSafely {
        repo.deleteCategory(id)
        if (store.settings.value.defaultCategoryId == id) store.update { it.copy(defaultCategoryId = 0) }
    }

    fun exportTo(uri: Uri, resolver: ContentResolver) = launchSafely {
        val data = repo.exportBackup()
        val json = BackupJson.toJson(data)
        withContext(Dispatchers.IO) {
            val out = resolver.openOutputStream(uri, "wt") ?: throw IllegalStateException("no stream")
            out.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        }
        messages.send("Exported ${data.tasks.size} tasks.")
    }

    fun importFrom(uri: Uri, resolver: ContentResolver) = launchSafely {
        val text = withContext(Dispatchers.IO) {
            val input = resolver.openInputStream(uri) ?: throw IllegalStateException("no stream")
            input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
        val result = repo.importBackup(BackupJson.parse(text))
        val extra = if (result.invalid > 0) ", ${result.invalid} unreadable entries ignored" else ""
        messages.send("Imported: ${result.added} new, ${result.updated} updated, ${result.skipped} unchanged$extra.")
    }

    fun clearCompleted() = launchSafely {
        val n = repo.clearCompleted()
        messages.send(if (n == 0) "No completed tasks to clear." else "Cleared $n completed tasks.")
    }

    fun clearAll() = launchSafely {
        repo.clearAll()
        messages.send("All data cleared.")
    }

    /** Runs [block], turning any failure into a friendly message instead of a crash. */
    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: BackupException) {
                messages.send(e.message ?: "Invalid backup file.")
            } catch (e: Exception) {
                messages.send("That didn't work. Please try again.")
            }
        }
    }
}
