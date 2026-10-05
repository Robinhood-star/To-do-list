package com.personal.todo

import android.content.Context
import com.personal.todo.data.AppDatabase
import com.personal.todo.data.SettingsStore
import com.personal.todo.data.TaskRepository
import com.personal.todo.reminders.AlarmReminderScheduler

/** Tiny manual dependency container (no DI framework needed for an app this size). */
class AppContainer(context: Context) {
    val settings = SettingsStore(context)
    val database: AppDatabase = AppDatabase.create(context)
    val repository = TaskRepository(database, AlarmReminderScheduler(context.applicationContext, settings))
}
