package com.personal.todo.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.personal.todo.data.SettingsStore
import com.personal.todo.data.TaskEntity
import com.personal.todo.domain.ReminderTime

interface ReminderScheduler {
    fun schedule(task: TaskEntity)
    fun cancel(taskId: Long)
}

/** Schedules one alarm per task with AlarmManager. Works offline and does not run anything in the background. */
class AlarmReminderScheduler(
    private val context: Context,
    private val settings: SettingsStore,
) : ReminderScheduler {

    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    private fun pendingIntent(taskId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_REMIND)
            .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(
            context, taskId.toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    override fun schedule(task: TaskEntity) {
        cancel(task.id)
        if (!settings.settings.value.remindersEnabled || task.isCompleted) return
        val trigger = ReminderTime.triggerMillis(task) ?: return
        if (trigger <= System.currentTimeMillis()) return
        val pi = pendingIntent(task.id)
        try {
            val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
            if (exactAllowed) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    override fun cancel(taskId: Long) {
        alarmManager.cancel(pendingIntent(taskId))
    }
}
