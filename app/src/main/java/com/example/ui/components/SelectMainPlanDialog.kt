package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

@Composable
fun SelectMainPlanDialog(
    indexEntries: List<PlanEntry>,
    currentPinnedHref: String?,
    initialCategory: PlanCategory,
    onSelectAndPin: (PlanEntry) -> Unit,
    onRefreshIndex: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by rememberSaveable { mutableStateOf(initialCategory) }
    var query by rememberSaveable { mutableStateOf("") }

    val filtered = remember(indexEntries, selectedCategory, query) {
        indexEntries
            .filter { it.category == selectedCategory }
            .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Ustaw mój główny plan",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Wybierz tryb ucznia (klasa), nauczyciela lub sali:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val roleOptions = listOf(
                        PlanCategory.CLASS to "Klasa",
                        PlanCategory.TEACHER to "Nauczyciel",
                        PlanCategory.ROOM to "Sala"
                    )
                    roleOptions.forEach { (cat, label) ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dialog_role_${cat.name.lowercase()}")
                        )
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(
                            when (selectedCategory) {
                                PlanCategory.CLASS -> "Szukaj klasy..."
                                PlanCategory.TEACHER -> "Szukaj nauczyciela..."
                                PlanCategory.ROOM -> "Szukaj sali..."
                            }
                        )
                    },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Wyczyść")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_plan_search")
                )

                if (filtered.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (indexEntries.isEmpty()) {
                                "Brak pobranej listy planów ze strony szkoły."
                            } else {
                                "Brak wyników dla „$query”."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (indexEntries.isEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = onRefreshIndex) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Pobierz listę")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filtered, key = { it.href }) { entry ->
                            val isPinned = entry.href == currentPinnedHref
                            val icon = when (entry.category) {
                                PlanCategory.CLASS -> Icons.Rounded.School
                                PlanCategory.TEACHER -> Icons.Rounded.Person
                                PlanCategory.ROOM -> Icons.Rounded.MeetingRoom
                            }
                            Surface(
                                color = if (isPinned) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        onSelectAndPin(entry)
                                        onDismiss()
                                    }
                                    .testTag("dialog_plan_item_${entry.name}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = if (isPinned) {
                                                MaterialTheme.colorScheme.onSecondaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = entry.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (isPinned) {
                                        Icon(
                                            imageVector = Icons.Rounded.Bookmark,
                                            contentDescription = "Aktualny główny plan",
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Zamknij")
            }
        }
    )
}
