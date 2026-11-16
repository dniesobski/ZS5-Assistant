package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.PlanEntry
import com.example.model.TeacherLocationStatus
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.viewmodel.MainUiState

@Composable
fun TeacherLocatorScreen(
    uiState: MainUiState,
    onQueryChange: (String) -> Unit,
    onTimeModeChange: (Boolean) -> Unit,
    onSelectDay: (Int) -> Unit,
    onSelectLessonNumber: (Int) -> Unit,
    onToggleOnlyTeaching: (Boolean) -> Unit,
    onSyncAllTeachers: () -> Unit,
    onOpenTeacherPlan: (PlanEntry) -> Unit,
    onPinTeacherPlan: (PlanEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val filter = uiState.teacherLocatorFilter

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("teacher_locator_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Gdzie jest nauczyciel?",
                style = MaterialTheme.typography.headlineMedium
            )

            OutlinedTextField(
                value = filter.query,
                onValueChange = onQueryChange,
                placeholder = { Text("Wpisz nazwisko, inicjały, salę lub klasę...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (filter.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Wyczyść wyszukiwanie")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("teacher_search_input")
            )

            // Time mode & active filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filter.useCurrentTime,
                    onClick = { onTimeModeChange(true) },
                    label = { Text("Teraz (na żywo)") },
                    leadingIcon = { Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("locator_mode_now")
                )
                FilterChip(
                    selected = !filter.useCurrentTime,
                    onClick = { onTimeModeChange(false) },
                    label = { Text("Wybierz lekcję") },
                    modifier = Modifier.testTag("locator_mode_slot")
                )
                FilterChip(
                    selected = filter.onlyTeachingNow,
                    onClick = { onToggleOnlyTeaching(!filter.onlyTeachingNow) },
                    label = { Text("Tylko prowadzący lekcję") },
                    modifier = Modifier.testTag("locator_only_active")
                )
            }

            if (!filter.useCurrentTime) {
                val days = listOf(1 to "Pon", 2 to "Wt", 3 to "Śr", 4 to "Czw", 5 to "Pt")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    days.forEach { (dow, label) ->
                        FilterChip(
                            selected = filter.selectedDayOfWeek == dow,
                            onClick = { onSelectDay(dow) },
                            label = { Text(label) }
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (0..10).forEach { nr ->
                        FilterChip(
                            selected = filter.selectedLessonNumber == nr,
                            onClick = { onSelectLessonNumber(nr) },
                            label = { Text("$nr. lekcja") }
                        )
                    }
                }
            }

            // Sync all teachers action card
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pobrane plany nauczycieli: ${uiState.cachedTeacherPlansCount}/${uiState.totalTeachersCount}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Zsynchronizuj wszystkie plany, aby wyszukiwać sale w czasie rzeczywistym",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onSyncAllTeachers,
                        enabled = !uiState.isSyncingTeachers,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("sync_all_teachers_button")
                    ) {
                        if (uiState.isSyncingTeachers) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Pobierz")
                        }
                    }
                }
            }
        }

        if (uiState.teacherLocations.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Rounded.PersonSearch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Brak pasujących nauczycieli",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Sprawdź pisownię lub pobierz indeks planów z zs5rybnik.pl/_plan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(uiState.teacherLocations, key = { it.teacher.href }) { status ->
                    TeacherLocationCard(
                        status = status,
                        isPinned = status.teacher.href == uiState.preferences.pinnedPlanHref,
                        onClick = { onOpenTeacherPlan(status.teacher) },
                        onPinClick = { onPinTeacherPlan(status.teacher) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TeacherLocationCard(
    status: TeacherLocationStatus,
    isPinned: Boolean,
    onClick: () -> Unit,
    onPinClick: () -> Unit
) {
    val containerColor = if (status.isCurrentlyTeaching) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (status.isCurrentlyTeaching) 2.dp else 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("teacher_card_${status.teacher.name}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    status.isCurrentlyTeaching -> MaterialTheme.colorScheme.tertiary
                                    status.hasCachedPlan -> MaterialTheme.colorScheme.outline
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = status.teacher.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(6.dp))

                when {
                    !status.hasCachedPlan -> {
                        Text(
                            text = "Plan niepobrany — kliknij Pobierz powyżej lub otwórz plan",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    status.isCurrentlyTeaching && status.currentLesson != null -> {
                        val sg = status.currentLesson
                        Text(
                            text = buildString {
                                append(sg.subject)
                                sg.className?.let { append(" • klasa $it") }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val timeMeta = listOfNotNull(
                            status.currentTimeRange,
                            status.minutesRemaining?.let { "do końca $it min" }
                        ).joinToString(" • ")
                        if (timeMeta.isNotBlank()) {
                            Text(
                                text = timeMeta,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                    else -> {
                        val nextInfo = status.nextLessonToday?.let { (slot, sg) ->
                            val roomPart = sg.roomName?.let { " w s. $it" } ?: ""
                            "Okienko • nast. ${slot.lessonNumber}. lekcja (${slot.timeRange})$roomPart"
                        } ?: "Brak dalszych lekcji w tym dniu"
                        Text(
                            text = nextInfo,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (status.isCurrentlyTeaching && status.currentLesson?.roomName != null) {
                Spacer(Modifier.width(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MeetingRoom,
                            contentDescription = "Sala",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = status.currentLesson.roomName,
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            IconButton(
                onClick = onPinClick,
                modifier = Modifier.testTag("pin_teacher_${status.teacher.name}")
            ) {
                Icon(
                    imageVector = if (isPinned) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    contentDescription = if (isPinned) {
                        "Główny plan nauczyciela"
                    } else {
                        "Ustaw plan nauczyciela jako mój główny plan"
                    },
                    tint = if (isPinned) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
