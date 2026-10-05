package com.personal.todo.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.personal.todo.TodoApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
        if (taskId == 0L) return
        val container = (context.applicationContext as TodoApp).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_REMIND -> {
                        val task = container.repository.getTask(taskId)
                        if (task != null && !task.isCompleted) NotificationHelper.show(context, task)
                    }
                    ACTION_DONE -> {
                        container.repository.complete(taskId)
                        NotificationHelper.cancel(context, taskId)
                    }
                }
            } catch (_: Exception) {
                // Never crash from a background broadcast.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "com.personal.todo.action.REMIND"
        const val ACTION_DONE = "com.personal.todo.action.DONE"
        const val EXTRA_TASK_ID = "task_id"
    }
}
