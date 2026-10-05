package com.personal.todo.data

import android.content.Context
import com.personal.todo.domain.AppConfig
import com.personal.todo.domain.Priority
import com.personal.todo.domain.SortType
import com.personal.todo.domain.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val retentionDays: Int = AppConfig.DEFAULT_RETENTION_DAYS,
    val defaultPriority: Priority = Priority.MEDIUM,
    /** 0 = no default category. */
    val defaultCategoryId: Long = 0,
    val remindersEnabled: Boolean = true,
    val sort: SortType = SortType.DUE,
)

/** Small SharedPreferences-backed settings holder exposed as a StateFlow. */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = state.asStateFlow()

    private inline fun <reified E : Enum<E>> enumPref(key: String, default: E): E =
        runCatching { enumValueOf<E>(prefs.getString(key, null) ?: "") }.getOrDefault(default)

    private fun load() = AppSettings(
        theme = enumPref("theme", ThemeMode.SYSTEM),
        retentionDays = prefs.getInt("retentionDays", AppConfig.DEFAULT_RETENTION_DAYS).coerceAtLeast(1),
        defaultPriority = enumPref("defaultPriority", Priority.MEDIUM),
        defaultCategoryId = prefs.getLong("defaultCategoryId", 0),
        remindersEnabled = prefs.getBoolean("remindersEnabled", true),
        sort = enumPref("sort", SortType.DUE),
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(state.value)
        state.value = next
        prefs.edit()
            .putString("theme", next.theme.name)
            .putInt("retentionDays", next.retentionDays)
            .putString("defaultPriority", next.defaultPriority.name)
            .putLong("defaultCategoryId", next.defaultCategoryId)
            .putBoolean("remindersEnabled", next.remindersEnabled)
            .putString("sort", next.sort.name)
            .apply()
    }
}
