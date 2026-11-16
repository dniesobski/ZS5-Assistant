package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.repository.PolishCalendarCalculator
import com.example.model.LessonCell
import com.example.model.PlanCategory
import com.example.model.PlanEntry
import com.example.model.ThemeMode
import com.example.ui.components.SelectMainPlanDialog
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.viewmodel.MainUiState

@Composable
fun TodayScreen(
    uiState: MainUiState,
    onRefresh: () -> Unit,
    onToggleTheme: (ThemeMode) -> Unit,
    onOpenPlansTab: () -> Unit,
    onOpenLocatorTab: () -> Unit,
    onOpenCalendarTab: () -> Unit,
    onOpenPlanHref: (String, String, PlanCategory) -> Unit,
    onPinEntry: (PlanEntry) -> Unit,
    onMarkChangesRead: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMainPlanDialog by remember { mutableStateOf(false) }

    if (showMainPlanDialog) {
        SelectMainPlanDialog(
            indexEntries = uiState.indexEntries,
            currentPinnedHref = uiState.preferences.pinnedPlanHref,
            initialCategory = uiState.preferences.pinnedPlanCategory ?: PlanCategory.CLASS,
            onSelectAndPin = onPinEntry,
            onRefreshIndex = onRefresh,
            onDismiss = { showMainPlanDialog = false }
        )
    }

    val countdown = uiState.holidayCountdown
    val pinnedPlan = uiState.pinnedPlan
    val todayDow = uiState.todayDayOfWeek.coerceIn(1, 5)
    val todayLessons = pinnedPlan?.lessons
        ?.filter { it.dayOfWeek == todayDow }
        ?.sortedBy { it.lessonNumber }
        .orEmpty()

    val isSchoolDayToday = uiState.todayDayOfWeek in 1..5 &&
        countdown.todayFreeEvent == null &&
        !countdown.isCurrentlySummerBreak

    val currentLesson = if (isSchoolDayToday) {
        todayLessons.firstOrNull {
            uiState.currentSecondOfDay in (it.startMinutes * 60) until (it.endMinutes * 60)
        }
    } else null

    val previousLesson = if (isSchoolDayToday) {
        todayLessons.lastOrNull { it.endMinutes * 60 <= uiState.currentSecondOfDay }
    } else null

    val nextLesson = if (isSchoolDayToday) {
        todayLessons.firstOrNull { it.startMinutes * 60 > uiState.currentSecondOfDay }
    } else null

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("today_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Banner Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(172.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_school_banner),
                        contentDescription = "Ilustracja Zespołu Szkół nr 5 w Rybniku",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x9908172A),
                                        Color(0xE608172A)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = Color(0x33FFFFFF),
                                shape = RoundedCornerShape(50)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.School,
                                        contentDescription = null,
                                        tint = Color(0xFFFBBF24),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "ZS nr 5 • Rybnik",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        val nextMode = when (uiState.preferences.themeMode) {
                                            ThemeMode.LIGHT -> ThemeMode.DARK
                                            ThemeMode.DARK -> ThemeMode.LIGHT
                                            ThemeMode.SYSTEM -> ThemeMode.DARK
                                        }
                                        onToggleTheme(nextMode)
                                    },
                                    modifier = Modifier.testTag("quick_theme_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (uiState.preferences.themeMode == ThemeMode.DARK) {
                                            Icons.Rounded.LightMode
                                        } else {
                                            Icons.Rounded.DarkMode
                                        },
                                        contentDescription = "Przełącz motyw jasny/ciemny",
                                        tint = Color.White
                                    )
                                }
                                IconButton(
                                    onClick = onRefresh,
                                    modifier = Modifier.testTag("refresh_today_button")
                                ) {
                                    if (uiState.isSyncingIndex || uiState.isSyncingPlan) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Refresh,
                                            contentDescription = "Odśwież plan lekcji",
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        Column {
                            Text(
                                text = PolishCalendarCalculator.formatDatePl(countdown.referenceDate)
                                    .replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFD6E6FF)
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val planLabel = pinnedPlan?.let {
                                    when (it.category) {
                                        PlanCategory.TEACHER -> "Plan nauczyciela: ${it.title}"
                                        PlanCategory.CLASS -> "Mój plan: ${it.title}"
                                        PlanCategory.ROOM -> "Sala: ${it.title}"
                                    }
                                } ?: "Wybierz swój plan lekcji"
                                Text(
                                    text = planLabel,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = { showMainPlanDialog = true },
                                    modifier = Modifier.testTag("change_pinned_plan_button")
                                ) {
                                    Text("Zmień", color = Color(0xFFFBBF24))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Network error banner if applicable
        if (uiState.syncError != null) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = uiState.syncError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onRefresh) {
                            Text("Ponów", color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
        }

        // Unread Schedule Changes Card
        if (uiState.unreadChangesCount > 0) {
            item {
                val latest = uiState.scheduleChanges.firstOrNull()
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Nowe zmiany w planie (${uiState.unreadChangesCount})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            TextButton(onClick = onMarkChangesRead) {
                                Text("OK")
                            }
                        }
                        if (latest != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = latest.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }

        // Current Lesson / Free Day Live Status Card
        item {
            LiveLessonStatusCard(
                isSchoolDayToday = isSchoolDayToday,
                todayFreeName = countdown.todayFreeEvent?.name,
                isWeekend = countdown.isWeekend,
                isSummerBreak = countdown.isCurrentlySummerBreak,
                previousLesson = previousLesson,
                currentLesson = currentLesson,
                nextLesson = nextLesson,
                currentMinuteOfDay = uiState.currentMinuteOfDay,
                currentSecondOfDay = uiState.currentSecondOfDay,
                hasPinnedPlan = pinnedPlan != null,
                onOpenPlansTab = onOpenPlansTab
            )
        }

        // Holiday & Summer Countdown Strip
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onOpenCalendarTab)
                        .testTag("today_holiday_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.EventAvailable,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Najbliższe wolne",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (countdown.todayFreeEvent != null) {
                                "DZIŚ"
                            } else {
                                "za ${countdown.daysUntilNextFreeDay} dni"
                            },
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = countdown.todayFreeEvent?.name
                                ?: countdown.nextFreeEvent?.name
                                ?: "Brak danych",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onOpenCalendarTab)
                        .testTag("today_summer_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.BeachAccess,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Do wakacji",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (countdown.isCurrentlySummerBreak) {
                                "TRWAJĄ!"
                            } else {
                                "${countdown.daysUntilSummer} dni"
                            },
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (countdown.isCurrentlySummerBreak) {
                                "Udanego wypoczynku"
                            } else {
                                "${countdown.schoolDaysUntilSummer} dni szkolnych"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // Quick Action Row: Teacher Locator & Full Calendar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = onOpenLocatorTab,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("today_locator_cta")
                ) {
                    Icon(Icons.Rounded.PersonSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Gdzie nauczyciel?", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FilledTonalButton(
                    onClick = onOpenCalendarTab,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("today_calendar_cta")
                ) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Kalendarz wolnego", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // Today's Schedule Header
        item {
            val dayLabel = when (todayDow) {
                1 -> "Poniedziałek"
                2 -> "Wtorek"
                3 -> "Środa"
                4 -> "Czwartek"
                else -> "Piątek"
            }
            Text(
                text = if (uiState.todayDayOfWeek in 1..5) {
                    "Dzisiejsze lekcje ($dayLabel)"
                } else {
                    "Podgląd planu (Poniedziałek)"
                },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (todayLessons.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = if (pinnedPlan == null) {
                                "Nie wybrano jeszcze klasy ani nauczyciela."
                            } else {
                                "Brak lekcji w wybranym dniu."
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onOpenPlansTab) {
                            Text("Przeglądaj plany lekcji")
                        }
                    }
                }
            }
        } else {
            items(todayLessons, key = { "${it.dayOfWeek}_${it.lessonNumber}" }) { cell ->
                val isActive = isSchoolDayToday && uiState.currentMinuteOfDay in cell.startMinutes until cell.endMinutes
                LessonRowCard(
                    cell = cell,
                    isActive = isActive,
                    onClickTeacher = { name, href ->
                        onOpenPlanHref(href, name, PlanCategory.TEACHER)
                    },
                    onClickRoom = { name, href ->
                        onOpenPlanHref(href, name, PlanCategory.ROOM)
                    },
                    onClickClass = { name, href ->
                        onOpenPlanHref(href, name, PlanCategory.CLASS)
                    }
                )
            }
        }
    }
}

@Composable
private fun LiveLessonStatusCard(
    isSchoolDayToday: Boolean,
    todayFreeName: String?,
    isWeekend: Boolean,
    isSummerBreak: Boolean,
    previousLesson: LessonCell?,
    currentLesson: LessonCell?,
    nextLesson: LessonCell?,
    currentMinuteOfDay: Int,
    currentSecondOfDay: Int,
    hasPinnedPlan: Boolean,
    onOpenPlansTab: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            when {
                isSummerBreak -> {
                    Text("Wakacje!", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.height(4.dp))
                    Text("Trwa przerwa wakacyjna — lekcje nie odbywają się.", style = MaterialTheme.typography.bodyMedium)
                }
                todayFreeName != null -> {
                    Text("Dzień wolny: $todayFreeName", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.height(4.dp))
                    Text("Dzisiaj nie ma zajęć lekcyjnych.", style = MaterialTheme.typography.bodyMedium)
                }
                isWeekend -> {
                    Text("Weekend", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text("Brak zajęć dydaktycznych w sobotę i niedzielę.", style = MaterialTheme.typography.bodyMedium)
                }
                !hasPinnedPlan -> {
                    Text("Połącz z planem ZS5 Rybnik", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Wybierz swoją klasę lub nauczyciela z listy, aby widzieć odliczanie do dzwonka i zmiany w planie.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onOpenPlansTab) {
                        Text("Wybierz klasę / nauczyciela")
                    }
                }
                currentLesson != null -> {
                    val sg = currentLesson.subgroups.firstOrNull()
                    val duration = (currentLesson.endMinutes - currentLesson.startMinutes).coerceAtLeast(1)
                    val elapsed = (currentMinuteOfDay - currentLesson.startMinutes).coerceIn(0, duration)
                    val remaining = (currentLesson.endMinutes - currentMinuteOfDay).coerceAtLeast(0)
                    val progress = elapsed.toFloat() / duration.toFloat()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(50)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondary)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "TRWA ${currentLesson.lessonNumber}. LEKCJA",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        Text(
                            text = "do dzwonka: $remaining min",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = sg?.subject ?: "Lekcja ${currentLesson.lessonNumber}",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(Modifier.height(4.dp))
                    val details = listOfNotNull(
                        sg?.roomName?.let { "Sala $it" },
                        sg?.teacherName,
                        sg?.className?.let { "Klasa $it" },
                        currentLesson.timeRange
                    ).joinToString(" • ")
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
                nextLesson != null -> {
                    val remainingSeconds = ((nextLesson.startMinutes * 60) - currentSecondOfDay).coerceAtLeast(0)
                    val totalMins = remainingSeconds / 60
                    val secs = remainingSeconds % 60
                    val sg = nextLesson.subgroups.firstOrNull()

                    if (previousLesson != null) {
                        val breakStartSec = previousLesson.endMinutes * 60
                        val breakEndSec = nextLesson.startMinutes * 60
                        val breakDurationSec = (breakEndSec - breakStartSec).coerceAtLeast(1)
                        val breakElapsedSec = (currentSecondOfDay - breakStartSec).coerceIn(0, breakDurationSec)
                        val breakProgress = breakElapsedSec.toFloat() / breakDurationSec.toFloat()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.testTag("break_status_badge")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.tertiary)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "TRWA PRZERWA",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                            Text(
                                text = "do końca: ${formatBreakCountdown(remainingSeconds)}",
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.testTag("break_countdown_text")
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = formatBreakTimerDigital(remainingSeconds),
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("break_timer_digital")
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Następna: ${nextLesson.lessonNumber}. ${sg?.subject ?: "Lekcja"}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = listOfNotNull(
                                sg?.roomName?.let { "Sala $it" },
                                sg?.teacherName,
                                nextLesson.timeRange
                            ).joinToString(" • "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { breakProgress },
                            color = MaterialTheme.colorScheme.tertiary,
                            trackColor = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    } else {
                        val countdownLabel = if (totalMins < 60) {
                            "Następna lekcja za ${totalMins} min ${secs.toString().padStart(2, '0')} s"
                        } else {
                            val hours = totalMins / 60
                            val remMins = totalMins % 60
                            "Pierwsza lekcja za $hours godz. $remMins min"
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.AccessTime,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = countdownLabel,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "${nextLesson.lessonNumber}. ${sg?.subject ?: "Lekcja"}",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = listOfNotNull(
                                sg?.roomName?.let { "Sala $it" },
                                sg?.teacherName,
                                nextLesson.timeRange
                            ).joinToString(" • "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    Text(
                        text = "Koniec zajęć na dziś",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Wszystkie zaplanowane na dzisiaj lekcje zostały zakończone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun LessonRowCard(
    cell: LessonCell,
    isActive: Boolean,
    onClickTeacher: ((String, String) -> Unit)? = null,
    onClickRoom: ((String, String) -> Unit)? = null,
    onClickClass: ((String, String) -> Unit)? = null
) {
    val containerColor = if (isActive) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left Lesson Number + Bell Time Pill
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(68.dp)
            ) {
                Surface(
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = cell.lessonNumber.toString(),
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = cell.timeRange.replace(" – ", "\n"),
                    fontFamily = JetBrainsMonoFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                cell.subgroups.forEachIndexed { idx, sg ->
                    if (idx > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        )
                    }
                    Column {
                        Text(
                            text = sg.subject,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            sg.roomName?.let { room ->
                                val href = sg.roomHref
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = if (href != null && onClickRoom != null) {
                                        Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onClickRoom(room, href) }
                                    } else Modifier
                                ) {
                                    Text(
                                        text = "s. $room",
                                        fontFamily = JetBrainsMonoFamily,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            sg.teacherName?.let { teacher ->
                                val href = sg.teacherHref
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = if (href != null && onClickTeacher != null) {
                                        Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onClickTeacher(teacher, href) }
                                    } else Modifier
                                ) {
                                    Text(
                                        text = teacher,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            sg.className?.let { cls ->
                                val href = sg.classHref
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = if (href != null && onClickClass != null) {
                                        Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onClickClass(cls, href) }
                                    } else Modifier
                                ) {
                                    Text(
                                        text = cls,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun formatBreakCountdown(remainingSeconds: Int): String {
    val safe = remainingSeconds.coerceAtLeast(0)
    val mins = safe / 60
    val secs = safe % 60
    return "$mins min ${secs.toString().padStart(2, '0')} s"
}

internal fun formatBreakTimerDigital(remainingSeconds: Int): String {
    val safe = remainingSeconds.coerceAtLeast(0)
    val mins = safe / 60
    val secs = safe % 60
    return "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
}
