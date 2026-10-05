package com.personal.todo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.todo.AppContainer
import com.personal.todo.data.CategoryEntity
import com.personal.todo.domain.FilterType
import com.personal.todo.domain.HeaderSummary
import com.personal.todo.domain.SectionGroup
import com.personal.todo.domain.SortType
import com.personal.todo.domain.TaskLogic
import com.personal.todo.domain.TaskQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** One-off message for the snackbar, optionally with an action (Undo). */
data class UiEvent(val message: String, val actionLabel: String? = null, val onAction: (() -> Unit)? = null)

data class HomeUiState(
    val loaded: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val summary: HeaderSummary = HeaderSummary(0, 0, 0, 0),
    val groups: List<SectionGroup> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val query: TaskQuery = TaskQuery(),
    val sort: SortType = SortType.DUE,
    val hasAnyTasks: Boolean = false,
)

class HomeViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.repository
    private val settings = container.settings

    private val query = MutableStateFlow(TaskQuery())
    private val today = MutableStateFlow(LocalDate.now())
    private val eventChannel = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        repo.tasks, repo.categories, query, today, settings.settings
    ) { tasks, cats, q, t, s ->
        HomeUiState(
            loaded = true, today = t,
            summary = TaskLogic.summary(tasks, t),
            groups = TaskLogic.build(tasks, cats, q, s.sort, t),
            categories = cats, query = q, sort = s.sort, hasAnyTasks = tasks.isNotEmpty(),
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun refreshToday() { today.value = LocalDate.now() }
    fun setFilter(f: FilterType) = query.update { it.copy(filter = f, date = null) }
    fun setCategory(id: Long?) = query.update { it.copy(categoryId = id) }
    fun setSearch(text: String) = query.update { it.copy(search = text) }
    fun setDate(date: LocalDate?) = query.update { it.copy(date = date) }
    fun setSort(sort: SortType) = settings.update { it.copy(sort = sort) }

    private fun MutableStateFlow<TaskQuery>.update(block: (TaskQuery) -> TaskQuery) { value = block(value) }

    fun toggle(id: Long, currentlyCompleted: Boolean) {
        if (currentlyCompleted) reopen(id) else complete(id)
    }

    fun complete(id: Long) = launchSafely {
        val result = repo.complete(id)
        eventChannel.send(UiEvent("Task completed", "Undo") {
            launchSafely { repo.undoComplete(id, result.spawnedId) }
        })
    }

    fun reopen(id: Long) = launchSafely { repo.reopen(id) }

    fun delete(id: Long) = launchSafely {
        val snapshot = repo.delete(id) ?: return@launchSafely
        eventChannel.send(UiEvent("Task deleted", "Undo") { launchSafely { repo.restore(snapshot) } })
    }

    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                eventChannel.send(UiEvent("Something went wrong. Please try again."))
            }
        }
    }
}
