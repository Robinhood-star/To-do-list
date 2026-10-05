package com.personal.todo.ui

import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.todo.data.CategoryEntity
import com.personal.todo.domain.Priority
import com.personal.todo.domain.ThemeMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private sealed interface Dialog {
    data object None : Dialog
    data object AddCategory : Dialog
    data class EditCategory(val category: CategoryEntity) : Dialog
    data class DeleteCategory(val category: CategoryEntity) : Dialog
    data object ClearCompleted : Dialog
    data object ClearAll : Dialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<Dialog>(Dialog.None) }

    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportTo(uri, context.contentResolver)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFrom(uri, context.contentResolver)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Title("Appearance")
            Chips {
                ThemeMode.entries.forEach { m ->
                    FilterChip(settings.theme == m, { vm.setTheme(m) }, label = { Text(m.label) })
                }
            }

            Divider()
            Title("Tasks")
            Hint("Keep completed tasks for (applied each time the app starts)")
            Chips {
                com.personal.todo.domain.AppConfig.RETENTION_OPTIONS.forEach { d ->
                    FilterChip(settings.retentionDays == d, { vm.setRetention(d) }, label = { Text("$d days") })
                }
            }
            Hint("Default priority")
            Chips {
                Priority.entries.forEach { p ->
                    FilterChip(settings.defaultPriority == p, { vm.setDefaultPriority(p) }, label = { Text(p.label) })
                }
            }
            Hint("Default category")
            Chips {
                FilterChip(settings.defaultCategoryId == 0L, { vm.setDefaultCategory(0) }, label = { Text("None") })
                categories.forEach { c ->
                    FilterChip(settings.defaultCategoryId == c.id, { vm.setDefaultCategory(c.id) }, label = { Text(c.name) })
                }
            }
            Hint("Categories")
            categories.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = { dialog = Dialog.EditCategory(c) }) { Icon(Icons.Default.Edit, contentDescription = "Rename ${c.name}") }
                    IconButton(onClick = { dialog = Dialog.DeleteCategory(c) }) { Icon(Icons.Default.Delete, contentDescription = "Delete ${c.name}") }
                }
            }
            OutlinedButton(onClick = { dialog = Dialog.AddCategory }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("  Add category")
            }

            Divider()
            Title("Notifications")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Enable reminders", style = MaterialTheme.typography.bodyLarge)
                    Hint("Turning this off cancels all scheduled reminders. Your settings per task are kept.")
                }
                Switch(settings.remindersEnabled, { vm.setRemindersEnabled(it) })
            }
            OutlinedButton(onClick = { openNotificationSettings(context) }) { Text("Open notification settings") }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
            ) {
                Hint("Exact alarms are off, so reminders may arrive a few minutes late.")
                OutlinedButton(onClick = { openExactAlarmSettings(context) }) { Text("Allow exact alarms") }
            }

            Divider()
            Title("Data")
            Hint("Everything is stored only on this phone. Use export to keep a backup file.")
            OutlinedButton(
                onClick = { exportLauncher.launch("my-tasks-backup-${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.json") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Export tasks (JSON)") }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Import tasks (JSON)") }
            OutlinedButton(onClick = { dialog = Dialog.ClearCompleted }, modifier = Modifier.fillMaxWidth()) { Text("Clear completed tasks") }
            OutlinedButton(onClick = { dialog = Dialog.ClearAll }, modifier = Modifier.fillMaxWidth()) {
                Text("Clear all data", color = MaterialTheme.colorScheme.error)
            }

            Divider()
            Title("About")
            val version = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0"
            }
            Text("My Tasks  v$version", style = MaterialTheme.typography.bodyLarge)
            Hint("A private, offline to-do list. No account, no ads, no analytics, no network access.")
        }
    }

    when (val d = dialog) {
        Dialog.None -> Unit
        Dialog.AddCategory -> NameDialog("New category", "", { vm.addCategory(it); dialog = Dialog.None }, { dialog = Dialog.None })
        is Dialog.EditCategory -> NameDialog("Rename category", d.category.name, { vm.renameCategory(d.category.id, it); dialog = Dialog.None }, { dialog = Dialog.None })
        is Dialog.DeleteCategory -> ConfirmDialog(
            "Delete \"${d.category.name}\"?", "Tasks in this category keep existing but will have no category.", "Delete",
            { vm.deleteCategory(d.category.id); dialog = Dialog.None }, { dialog = Dialog.None },
        )
        Dialog.ClearCompleted -> ConfirmDialog(
            "Clear completed tasks?", "All completed tasks will be permanently removed.", "Clear",
            { vm.clearCompleted(); dialog = Dialog.None }, { dialog = Dialog.None },
        )
        Dialog.ClearAll -> ConfirmDialog(
            "Clear all data?", "Every task, subtask and custom category will be permanently deleted. Export a backup first if unsure.", "Delete everything",
            { vm.clearAll(); dialog = Dialog.None }, { dialog = Dialog.None },
        )
    }
}

@Composable private fun Title(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))

@Composable private fun Hint(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable private fun Divider() = HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

@Composable
private fun Chips(content: @Composable () -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    try { context.startActivity(intent) } catch (_: ActivityNotFoundException) { }
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
    try { context.startActivity(intent) } catch (_: ActivityNotFoundException) { }
}
