package com.personal.todo.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.personal.todo.TodoApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Re-registers all alarms after reboot, app update, clock/timezone change or exact-alarm permission change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as TodoApp).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.repository.rescheduleAll()
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}
