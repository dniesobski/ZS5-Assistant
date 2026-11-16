package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.PlanCategory
import com.example.model.PlanEntry
import com.example.ui.viewmodel.MainUiState

@Composable
fun TimetableScreen(
    uiState: MainUiState,
    onSelectEntry: (PlanEntry) -> Unit,
    onPinEntry: (PlanEntry) -> Unit,
    onOpenPlanHref: (String, String, PlanCategory) -> Unit,
    onRefreshIndex: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by rememberSaveable { mutableStateOf(PlanCategory.CLASS) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedDay by rememberSaveable {
        mutableIntStateOf(uiState.todayDayOfWeek.coerceIn(1, 5))
    }
    var showSelectorList by rememberSaveable { mutableStateOf(false) }

    val categoryEntries = remember(uiState.indexEntries, selectedCategory, searchQuery) {
        uiState.indexEntries
            .filter { it.category == selectedCategory }
            .filter { searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) }
    }

    val viewedPlan = uiState.viewedPlan
    val selectedEntry = uiState.selectedPlanEntry
    val isPinned = selectedEntry != null && selectedEntry.href == uiState.preferences.pinnedPlanHref

    LaunchedEffect(selectedEntry?.href) {
        if (selectedEntry != null && !showSelectorList) {
            selectedCategory = selectedEntry.category
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("timetable_screen")
    ) {
        // Category Tabs: Klasy / Nauczyciele / Sale
        TabRow(selectedTabIndex = selectedCategory.ordinal) {
            PlanCategory.entries.forEach { cat ->
                val icon = when (cat) {
                    PlanCategory.CLASS -> Icons.Rounded.School
                    PlanCategory.TEACHER -> Icons.Rounded.Person
                    PlanCategory.ROOM -> Icons.Rounded.MeetingRoom
                }
                Tab(
                    selected = selectedCategory == cat,
                    onClick = {
                        selectedCategory = cat
                        showSelectorList = true
                    },
                    text = { Text(cat.labelPl) },
                    icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("plan_tab_${cat.name.lowercase()}")
                )
            }
        }

        // Search + Quick Entry Chips
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        showSelectorList = true
                    },
                    placeholder = { Text("Szukaj (${selectedCategory.labelPl.lowercase()})...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Wyczyść wyszukiwanie")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("plan_search_input")
                )
                IconButton(
                    onClick = onRefreshIndex,
                    modifier = Modifier.testTag("refresh_index_button")
                ) {
                    if (uiState.isSyncingIndex) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    } else {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Odśwież listę planów")
                    }
                }
            }

            if (categoryEntries.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categoryEntries.take(25), key = { it.href }) { entry ->
                        val selected = selectedEntry?.href == entry.href
                        FilterChip(
                            selected = selected,
                            onClick = {
                                showSelectorList = false
                                onSelectEntry(entry)
                            },
                            label = { Text(entry.name) },
                            modifier = Modifier.testTag("plan_chip_${entry.name}")
                        )
                    }
                }
            }
        }

        if (showSelectorList && categoryEntries.isNotEmpty()) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(categoryEntries, key = { it.href }) { entry ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                showSelectorList = false
                                onSelectEntry(entry)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(entry.name, style = MaterialTheme.typography.titleMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (entry.href == uiState.preferences.pinnedPlanHref) {
                                    Icon(
                                        imageVector = Icons.Rounded.Bookmark,
                                        contentDescription = "Główny plan",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    text = entry.category.labelPl,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        } else if (viewedPlan == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (uiState.isSyncingPlan || uiState.isSyncingIndex) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Pobieranie planu z zs5rybnik.pl/_plan...")
                } else {
                    Text(
                        text = "Brak załadowanego planu",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = uiState.syncError ?: "Wybierz klasę, nauczyciela lub salę powyżej albo pobierz listę ze strony szkoły.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onRefreshIndex) {
                        Text("Pobierz listę z zs5rybnik.pl")
                    }
                }
            }
        } else {
            // Active Plan Header + Day Selector
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = viewedPlan.title,
                                style = MaterialTheme.typography.headlineSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (viewedPlan.generatedAt.isNotBlank()) {
                                Text(
                                    text = viewedPlan.generatedAt,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (selectedEntry != null) {
                            Spacer(Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = { onPinEntry(selectedEntry) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.testTag("pin_plan_button")
                            ) {
                                Icon(
                                    imageVector = if (isPinned) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                    contentDescription = null,
                                    tint = if (isPinned) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (isPinned) "Główny plan" else "Ustaw jako główny",
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    val days = listOf(1 to "Pon", 2 to "Wt", 3 to "Śr", 4 to "Czw", 5 to "Pt")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        days.forEach { (dow, label) ->
                            FilterChip(
                                selected = selectedDay == dow,
                                onClick = { selectedDay = dow },
                                label = { Text(label) },
                                modifier = Modifier.testTag("day_chip_$dow")
                            )
                        }
                    }
                }
            }

            val dayLessons = remember(viewedPlan, selectedDay) {
                viewedPlan.lessons
                    .filter { it.dayOfWeek == selectedDay }
                    .sortedBy { it.lessonNumber }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (dayLessons.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Brak zaplanowanych lekcji w tym dniu.",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    }
                } else {
                    items(dayLessons, key = { "${it.dayOfWeek}_${it.lessonNumber}" }) { cell ->
                        val isNow = selectedDay == uiState.todayDayOfWeek &&
                            uiState.currentMinuteOfDay in cell.startMinutes until cell.endMinutes
                        LessonRowCard(
                            cell = cell,
                            isActive = isNow,
                            onClickTeacher = { name, href ->
                                selectedCategory = PlanCategory.TEACHER
                                onOpenPlanHref(href, name, PlanCategory.TEACHER)
                            },
                            onClickRoom = { name, href ->
                                selectedCategory = PlanCategory.ROOM
                                onOpenPlanHref(href, name, PlanCategory.ROOM)
                            },
                            onClickClass = { name, href ->
                                selectedCategory = PlanCategory.CLASS
                                onOpenPlanHref(href, name, PlanCategory.CLASS)
                            }
                        )
                    }
                }
            }
        }
    }
}
