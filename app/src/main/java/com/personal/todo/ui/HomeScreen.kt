package com.personal.todo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.todo.domain.FilterType
import com.personal.todo.domain.Priority
import com.personal.todo.domain.Recurrence
import com.personal.todo.domain.Section
import com.personal.todo.domain.SortType
import com.personal.todo.domain.TaskRow
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: HomeViewModel, onAdd: () -> Unit, onOpen: (Long) -> Unit, onSettings: () -> Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var searchOpen by rememberSaveable { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshToday() }
    LaunchedEffect(Unit) {
        vm.events.collectLatest { e ->
            val result = snackbar.showSnackbar(e.message, e.actionLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) e.onAction?.invoke()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    IconButton(onClick = {
                        if (searchOpen) vm.setSearch("")
                        searchOpen = !searchOpen
                    }) { Icon(Icons.Default.Search, contentDescription = "Search tasks") }
                    IconButton(onClick = {
                        showDatePicker(context, state.query.date ?: state.today) { vm.setDate(it) }
                    }) { Icon(Icons.Default.DateRange, contentDescription = "Pick a date") }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) { Icon(Icons.Default.Add, contentDescription = "Add task") }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "header") { Header(state) }

            if (searchOpen) {
                item(key = "search") {
                    val focus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                    OutlinedTextField(
                        value = state.query.search,
                        onValueChange = vm::setSearch,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus),
                        placeholder = { Text("Search title, notes, category") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { vm.setSearch(""); searchOpen = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close search")
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                    )
                }
            }

            item(key = "filters") { FilterRow(state, vm) }

            state.query.date?.let { date ->
                item(key = "dateChip") {
                    Row(Modifier.padding(horizontal = 16.dp)) {
                        FilterChip(
                            selected = true,
                            onClick = { vm.setDate(null) },
                            label = { Text("Tasks on ${formatDay(date, state.today)}") },
                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Clear date filter", modifier = Modifier.size(18.dp)) },
                        )
                    }
                }
            }

            if (state.loaded && state.groups.isEmpty()) {
                item(key = "empty") { EmptyState(filtered = state.hasAnyTasks) }
            }

            state.groups.forEach { group ->
                item(key = "h-${group.section.name}") { SectionHeader(group.section, group.rows.size) }
                items(group.rows, key = { "${group.section.name}-${it.task.id}" }) { row ->
                    TaskCard(
                        row = row,
                        today = state.today,
                        onToggle = { vm.toggle(row.task.id, row.task.isCompleted) },
                        onOpen = { onOpen(row.task.id) },
                        onSwipeComplete = { if (row.task.isCompleted) vm.reopen(row.task.id) else vm.complete(row.task.id) },
                        onSwipeDelete = { vm.delete(row.task.id) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(state: HomeUiState) {
    val now = remember(state.today) { LocalDateTime.now() }
    val greeting = when (now.hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(greeting, style = MaterialTheme.typography.headlineMedium)
        Text(
            state.today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatPill(state.summary.todayTotal, "Today", Modifier.weight(1f))
            StatPill(state.summary.todayDone, "Completed", Modifier.weight(1f))
            StatPill(state.summary.todayRemaining, "Remaining", Modifier.weight(1f))
            if (state.summary.overdue > 0) {
                StatPill(state.summary.overdue, "Overdue", Modifier.weight(1f), MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun StatPill(count: Int, label: String, modifier: Modifier, accent: Color = MaterialTheme.colorScheme.onSurface) {
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$count $label" },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(count.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FilterRow(state: HomeUiState, vm: HomeViewModel) {
    var categoryMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val selectedCategory = state.categories.firstOrNull { it.id == state.query.categoryId }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(FilterType.entries.toList()) { f ->
            FilterChip(
                selected = state.query.date == null && state.query.filter == f,
                onClick = { vm.setFilter(f) },
                label = { Text(f.label) },
            )
        }
        item {
            Box {
                FilterChip(
                    selected = selectedCategory != null,
                    onClick = { categoryMenu = true },
                    label = { Text(selectedCategory?.name ?: "Category") },
                )
                DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                    DropdownMenuItem(text = { Text("All categories") }, onClick = { vm.setCategory(null); categoryMenu = false })
                    state.categories.forEach { c ->
                        DropdownMenuItem(text = { Text(c.name) }, onClick = { vm.setCategory(c.id); categoryMenu = false })
                    }
                }
            }
        }
        item {
            Box {
                AssistChip(onClick = { sortMenu = true }, label = { Text("Sort: ${state.sort.label}") })
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    SortType.entries.forEach { s ->
                        DropdownMenuItem(text = { Text(s.label) }, onClick = { vm.setSort(s); sortMenu = false })
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(section: Section, count: Int) {
    val color = if (section == Section.OVERDUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.padding(start = 20.dp, end = 16.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(section.title.uppercase(), style = MaterialTheme.typography.labelLarge, color = color)
        Spacer(Modifier.width(8.dp))
        Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun EmptyState(filtered: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (filtered) "No matching tasks" else "You're all clear", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            if (filtered) "Try a different filter or search." else "Tap + to add your first task.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskCard(
    row: TaskRow,
    today: LocalDate,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onSwipeComplete: () -> Unit,
    onSwipeDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val task = row.task
    val complete by rememberUpdatedState(onSwipeComplete)
    val delete by rememberUpdatedState(onSwipeDelete)
    val dismissState = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
        when (value) {
            SwipeToDismissBoxValue.StartToEnd -> { complete(); true }
            SwipeToDismissBoxValue.EndToStart -> { delete(); true }
            else -> false
        }
    })
    val shape = RoundedCornerShape(16.dp)
    val priority = Priority.from(task.priority)
    val overdue = isOverdue(task, today)

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            val dir = dismissState.dismissDirection
            val bg = when (dir) {
                SwipeToDismissBoxValue.StartToEnd -> Color(0xFF2E9E6B)
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                else -> Color.Transparent
            }
            Box(
                Modifier.fillMaxSize().clip(shape).background(bg).padding(horizontal = 20.dp),
                contentAlignment = if (dir == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                if (dir != SwipeToDismissBoxValue.Settled) {
                    Icon(
                        if (dir == SwipeToDismissBoxValue.StartToEnd) Icons.Default.Check else Icons.Default.Delete,
                        contentDescription = null, tint = Color.White,
                    )
                }
            }
        },
    ) {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable(onClick = onOpen),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(4.dp).fillMaxHeight().background(priority.color()))
                Checkbox(
                    checked = task.isCompleted,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.semantics {
                        contentDescription = if (task.isCompleted) "Mark ${task.title} as not done" else "Mark ${task.title} as done"
                    },
                )
                Column(Modifier.weight(1f).padding(top = 10.dp, bottom = 10.dp, end = 12.dp)) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                        color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        val muted = MaterialTheme.colorScheme.onSurfaceVariant
                        formatDue(task, today)?.let {
                            Text(it, fontSize = 13.sp, color = if (overdue) MaterialTheme.colorScheme.error else muted)
                        }
                        row.categoryName?.let { Text("#$it", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary) }
                        if (!task.isCompleted && priority >= Priority.HIGH) {
                            Text(priority.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = priority.color())
                        }
                        if (row.subtasksTotal > 0) {
                            Text("${row.subtasksDone}/${row.subtasksTotal}", fontSize = 13.sp, color = muted)
                        }
                        if (Recurrence.from(task.recurrence) != Recurrence.NONE) {
                            Text("Repeats", fontSize = 13.sp, color = muted)
                        }
                        if (task.reminderEnabled && !task.isCompleted) {
                            Icon(
                                Icons.Default.Notifications, contentDescription = "Reminder on",
                                modifier = Modifier.size(15.dp), tint = muted,
                            )
                        }
                    }
                }
            }
        }
    }
}
