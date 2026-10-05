package com.personal.todo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.todo.domain.Priority
import com.personal.todo.domain.Recurrence
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ReminderOffsets = listOf(
    0 to "At due time", 5 to "5 min before", 15 to "15 min before",
    30 to "30 min before", 60 to "1 hour before", 1440 to "1 day before",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTaskScreen(vm: EditViewModel, onClose: () -> Unit) {
    val form = vm.form
    val categories by vm.categories.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val titleFocus = remember { FocusRequester() }
    var pendingSubtask by rememberSaveable { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }
    val tomorrow = today.plusDays(1)

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) scope.launch {
            snackbar.showSnackbar("Notifications are blocked, so reminders won't appear. You can allow them in system settings.")
        }
    }

    LaunchedEffect(vm.missing) { if (vm.missing) onClose() }
    LaunchedEffect(vm.loaded) { if (vm.loaded && vm.isNew) runCatching { titleFocus.requestFocus() } }

    fun save() {
        scope.launch {
            when (val r = vm.save(pendingSubtask)) {
                SaveResult.Ok -> onClose()
                is SaveResult.Error -> snackbar.showSnackbar(r.message)
            }
        }
    }

    fun pickDate() = showDatePicker(context, form.date ?: today) { d -> vm.update { it.copy(date = d) } }
    fun pickTime() {
        val m = form.minutes ?: 9 * 60
        showTimePicker(context, m / 60, m % 60) { h, min -> vm.update { it.copy(minutes = h * 60 + min) } }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isNew) "New task" else "Task details") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (!vm.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete task")
                        }
                    }
                    TextButton(onClick = { save() }) { Text("Save") }
                },
            )
        },
    ) { padding ->
        if (!vm.loaded) return@Scaffold
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            OutlinedTextField(
                value = form.title,
                onValueChange = { v -> vm.update { it.copy(title = v) } },
                modifier = Modifier.fillMaxWidth().focusRequester(titleFocus),
                label = { Text("What needs doing?") },
                placeholder = { Text("e.g. Call Pankaj tomorrow 10 AM") },
                isError = vm.titleError,
                supportingText = if (vm.titleError) ({ Text("A title is required") }) else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
            )

            Labeled("Due") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(form.date == today, { vm.update { it.copy(date = today) } }, label = { Text("Today") })
                    FilterChip(form.date == tomorrow, { vm.update { it.copy(date = tomorrow) } }, label = { Text("Tomorrow") })
                    val other = form.date != null && form.date != today && form.date != tomorrow
                    FilterChip(
                        other, { pickDate() },
                        label = { Text(if (other) formatDay(form.date!!, today) else "Pick date") },
                    )
                    FilterChip(
                        form.date == null,
                        { vm.update { it.copy(date = null, minutes = null, reminderEnabled = false) } },
                        label = { Text("No date") },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = form.minutes != null,
                        onClick = { pickTime() },
                        label = { Text(form.minutes?.let { formatMinutes(it) } ?: "Set time") },
                    )
                    if (form.minutes != null) {
                        TextButton(onClick = { vm.update { it.copy(minutes = null) } }) { Text("Clear time") }
                    }
                }
            }

            Labeled("Priority") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Priority.entries.forEach { p ->
                        FilterChip(
                            selected = form.priority == p,
                            onClick = { vm.update { it.copy(priority = p) } },
                            label = { Text(p.label) },
                            leadingIcon = { Box(Modifier.size(10.dp).background(p.color(), CircleShape)) },
                        )
                    }
                }
            }

            Labeled("Category") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(form.categoryId == null, { vm.update { it.copy(categoryId = null) } }, label = { Text("None") })
                    categories.forEach { c ->
                        FilterChip(form.categoryId == c.id, { vm.update { it.copy(categoryId = c.id) } }, label = { Text(c.name) })
                    }
                    FilterChip(
                        false, { newCategory = true }, label = { Text("New") },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }

            Labeled("Reminder") {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Remind me", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = form.reminderEnabled,
                        onCheckedChange = { on ->
                            vm.update { it.copy(reminderEnabled = on) }
                            val needsPermission = Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            if (on && needsPermission) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                    )
                }
                if (form.reminderEnabled) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReminderOffsets.forEach { (minutes, label) ->
                            FilterChip(form.reminderOffset == minutes, { vm.update { it.copy(reminderOffset = minutes) } }, label = { Text(label) })
                        }
                    }
                    if (form.minutes == null) {
                        Text(
                            "No time set, so the reminder will fire at 9:00 AM.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Labeled("Repeat") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Recurrence.entries.forEach { r ->
                        FilterChip(form.recurrence == r, { vm.update { it.copy(recurrence = r) } }, label = { Text(r.label) })
                    }
                }
                if (form.recurrence == Recurrence.CUSTOM) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Every")
                        OutlinedTextField(
                            value = form.interval.toString(),
                            onValueChange = { v -> v.filter(Char::isDigit).take(3).toIntOrNull()?.let { n -> vm.update { it.copy(interval = n) } } },
                            modifier = Modifier.width(88.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        Text("days")
                    }
                }
                if (form.recurrence != Recurrence.NONE) {
                    Text(
                        "The next occurrence is created when you complete this task.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                value = form.description,
                onValueChange = { v -> vm.update { it.copy(description = v) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notes") },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            Labeled("Subtasks") {
                if (form.subtasks.isNotEmpty()) {
                    val done = form.subtasks.count { it.done }
                    Text("$done/${form.subtasks.size} completed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(progress = { done.toFloat() / form.subtasks.size }, modifier = Modifier.fillMaxWidth())
                }
                form.subtasks.forEach { s ->
                    androidx.compose.runtime.key(s.key) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(checked = s.done, onCheckedChange = { c -> vm.updateSubtask(s.key) { it.copy(done = c) } })
                            TextField(
                                value = s.title,
                                onValueChange = { v -> vm.updateSubtask(s.key) { it.copy(title = v) } },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                                ),
                            )
                            IconButton(onClick = { vm.removeSubtask(s.key) }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove subtask ${s.title}")
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = pendingSubtask,
                    onValueChange = { pendingSubtask = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Add a subtask") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.addSubtask(pendingSubtask); pendingSubtask = "" }),
                    trailingIcon = {
                        IconButton(onClick = { vm.addSubtask(pendingSubtask); pendingSubtask = "" }) {
                            Icon(Icons.Default.Add, contentDescription = "Add subtask")
                        }
                    },
                )
            }

            if (!vm.isNew) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(form.completed, { c -> vm.update { it.copy(completed = c) } })
                    Text("Completed")
                }
                val fmt = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.getDefault())
                fun stamp(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(fmt)
                Column {
                    vm.createdAt?.let { Text("Created ${stamp(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    vm.completedAt?.let { Text("Completed ${stamp(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this task?", message = "This can't be undone from here.", confirmLabel = "Delete",
            onConfirm = { confirmDelete = false; scope.launch { if (vm.delete()) onClose() else snackbar.showSnackbar("Couldn't delete the task.") } },
            onDismiss = { confirmDelete = false },
        )
    }
    if (newCategory) {
        NameDialog(
            title = "New category", initial = "",
            onConfirm = { name -> newCategory = false; scope.launch { vm.addCategory(name) } },
            onDismiss = { newCategory = false },
        )
    }
}

@Composable
private fun Labeled(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}
