package com.personal.todo

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.todo.domain.ThemeMode
import com.personal.todo.ui.AppNav
import com.personal.todo.ui.TodoTheme

class MainActivity : ComponentActivity() {
    private val openTaskId = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openTaskId.value = readTaskId(intent)
        val container = (application as TodoApp).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val barStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
            enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            TodoTheme(dark) { AppNav(container, openTaskId) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openTaskId.value = readTaskId(intent)
    }

    private fun readTaskId(intent: Intent?): Long? =
        intent?.getLongExtra(EXTRA_OPEN_TASK, 0L)?.takeIf { it != 0L }

    companion object {
        const val EXTRA_OPEN_TASK = "open_task_id"
    }
}
