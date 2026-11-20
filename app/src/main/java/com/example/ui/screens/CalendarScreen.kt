package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.repository.PolishCalendarCalculator
import com.example.model.HolidayEvent
import com.example.model.SimpleDate
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.viewmodel.MainUiState
import java.util.Calendar
import java.util.TimeZone

@Composable
fun CalendarScreen(
    uiState: MainUiState,
    onAddDirectorDay: (SimpleDate, SimpleDate, String) -> Unit,
    onRemoveDirectorDay: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val countdown = uiState.holidayCountdown
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AddDirectorDayDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { startDate, endDate, name ->
                onAddDirectorDay(startDate, endDate, name)
                showAddDialog = false
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("calendar_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Kalendarz dni wolnych",
                    style = MaterialTheme.typography.headlineMedium
                )
                FilledTonalButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.testTag("calendar_add_director_day_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Dyrektorski")
                }
            }
        }

        // Nearest Free Day Hero Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.EventAvailable,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Najbliższy dzień wolny od szkoły",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    if (countdown.todayFreeEvent != null) {
                        Text(
                            text = "DZISIAJ WOLNE!",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = countdown.todayFreeEvent.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    } else if (countdown.nextFreeEvent != null) {
                        val daysLabel = if (countdown.daysUntilNextFreeDay == 1L) "1 dzień" else "${countdown.daysUntilNextFreeDay} dni"
                        Text(
                            text = "za $daysLabel",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = countdown.nextFreeEvent.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = PolishCalendarCalculator.formatDatePl(countdown.nextFreeEvent.date),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        // Summer Vacation Countdown Hero Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.BeachAccess,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Odliczanie do wakacji",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    if (countdown.isCurrentlySummerBreak) {
                        Text(
                            text = "TRWAJĄ WAKACJE!",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Następne zakończenie roku szkolnego: ${PolishCalendarCalculator.formatDatePl(countdown.summerVacationDate)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    text = "${countdown.daysUntilSummer} dni",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.displaySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "kalendarzowych do zakończenia roku",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = "${countdown.schoolDaysUntilSummer}",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "dni szkolnych",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Zakończenie roku: ${PolishCalendarCalculator.formatDatePl(countdown.summerVacationDate)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // Upcoming Polish Holidays & Director Days
        item {
            Text(
                text = "Nadchodzące święta i dni dyrektorskie",
                style = MaterialTheme.typography.titleLarge
            )
        }

        items(countdown.upcomingEvents, key = { "${it.date.year}_${it.date.month}_${it.date.day}_${it.name}" }) { event ->
            val daysAway = PolishCalendarCalculator.daysBetween(countdown.referenceDate, event.date)
            HolidayEventRow(
                event = event,
                daysAway = daysAway,
                onRemoveDirectorDay = onRemoveDirectorDay
            )
        }
    }
}

@Composable
private fun HolidayEventRow(
    event: HolidayEvent,
    daysAway: Long,
    onRemoveDirectorDay: (Long) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    color = if (event.isDirectorDay) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Celebration,
                        contentDescription = null,
                        tint = if (event.isDirectorDay) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = event.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = PolishCalendarCalculator.formatDatePl(event.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (event.isDirectorDay) {
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Dzień dyrektorski",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "za $daysAway d.",
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                if (event.isDirectorDay && event.directorDayId != null) {
                    IconButton(onClick = { onRemoveDirectorDay(event.directorDayId) }) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Usuń dzień dyrektorski",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDirectorDayDialog(
    onDismiss: () -> Unit,
    onConfirm: (SimpleDate, SimpleDate, String) -> Unit
) {
    val rangePickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = System.currentTimeMillis(),
        initialSelectedEndDateMillis = null
    )
    var stepPickLabel by remember { mutableStateOf(false) }
    var chosenStartDate by remember { mutableStateOf(PolishCalendarCalculator.today()) }
    var chosenEndDate by remember { mutableStateOf(PolishCalendarCalculator.today()) }
    var labelText by remember { mutableStateOf("Dzień dyrektorski") }

    fun millisToSimpleDate(millis: Long): SimpleDate {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = millis
        }
        return SimpleDate(
            year = cal.get(Calendar.YEAR),
            month = cal.get(Calendar.MONTH) + 1,
            day = cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    if (!stepPickLabel) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                Button(
                    onClick = {
                        val startMillis = rangePickerState.selectedStartDateMillis
                        val endMillis = rangePickerState.selectedEndDateMillis
                        if (startMillis != null) {
                            chosenStartDate = millisToSimpleDate(startMillis)
                            chosenEndDate = if (endMillis != null) {
                                millisToSimpleDate(endMillis)
                            } else {
                                chosenStartDate
                            }
                        }
                        stepPickLabel = true
                    },
                    enabled = rangePickerState.selectedStartDateMillis != null,
                    modifier = Modifier.testTag("director_range_next_btn")
                ) {
                    Text("Dalej")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Anuluj")
                }
            }
        ) {
            DateRangePicker(
                state = rangePickerState,
                title = {
                    Text(
                        text = "Wybierz dzień lub zakres dat (od – do)",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)
                    )
                },
                headline = {
                    val startMs = rangePickerState.selectedStartDateMillis
                    val endMs = rangePickerState.selectedEndDateMillis
                    val summary = when {
                        startMs == null -> "Wskaż dzień początkowy i końcowy"
                        endMs == null || endMs == startMs -> {
                            "1 dzień: ${PolishCalendarCalculator.formatShortDate(millisToSimpleDate(startMs))}"
                        }
                        else -> {
                            val s = millisToSimpleDate(startMs)
                            val e = millisToSimpleDate(endMs)
                            val count = (PolishCalendarCalculator.daysBetween(s, e) + 1).coerceAtLeast(1)
                            "${PolishCalendarCalculator.formatShortDate(s)} – ${PolishCalendarCalculator.formatShortDate(e)} ($count dni)"
                        }
                    }
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp)
                    )
                },
                showModeToggle = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    } else {
        val totalDays = (PolishCalendarCalculator.daysBetween(chosenStartDate, chosenEndDate) + 1).coerceAtLeast(1)
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(if (totalDays > 1) "Nazwa dni wolnych" else "Nazwa dnia wolnego")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (chosenStartDate == chosenEndDate) {
                            "Wybrana data: ${PolishCalendarCalculator.formatDatePl(chosenStartDate)}"
                        } else {
                            "Wybrany zakres: ${PolishCalendarCalculator.formatShortDate(chosenStartDate)} – ${PolishCalendarCalculator.formatShortDate(chosenEndDate)} ($totalDays dni)"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = labelText,
                        onValueChange = { labelText = it },
                        label = { Text("Okazja / opis") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("director_day_name_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onConfirm(
                            chosenStartDate,
                            chosenEndDate,
                            labelText.trim().ifBlank { "Dzień dyrektorski" }
                        )
                    },
                    modifier = Modifier.testTag("director_range_confirm_btn")
                ) {
                    Text(if (totalDays > 1) "Dodaj ($totalDays dni)" else "Dodaj")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { stepPickLabel = false }) {
                        Text("Zmień daty")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Anuluj")
                    }
                }
            }
        )
    }
}
