package com.personal.todo

import android.app.Application
import com.personal.todo.reminders.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TodoApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.createChannel(this)
        appScope.launch {
            try {
                // Automatic cleanup of old completed tasks, then make sure alarms match the database.
                container.repository.cleanupExpired(container.settings.settings.value.retentionDays)
                container.repository.rescheduleAll()
            } catch (_: Exception) {
                // A failed housekeeping pass must never stop the app from starting.
            }
        }
    }
}
