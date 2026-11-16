package com.example.data.repository

import com.example.data.local.CachedPlanEntity
import com.example.data.local.DirectorFreeDayEntity
import com.example.data.local.PlanIndexEntity
import com.example.data.local.PlanJsonSerializer
import com.example.data.local.ScheduleChangeEntity
import com.example.data.local.TimetableDao
import com.example.data.network.TimetableRemoteDataSource
import com.example.model.HolidayEvent
import com.example.model.LessonCell
import com.example.model.ParsedPlan
import com.example.model.PlanCategory
import com.example.model.PlanEntry
import com.example.model.SimpleDate
import com.example.model.TeacherLocationStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TimetableRepository(
    private val dao: TimetableDao,
    private val remote: TimetableRemoteDataSource
) {
    val indexEntries: Flow<List<PlanEntry>> = dao.observeIndex().map { list ->
        list.map {
            PlanEntry(
                href = it.href,
                name = it.name,
                category = runCatching { PlanCategory.valueOf(it.category) }.getOrDefault(PlanCategory.CLASS)
            )
        }
    }

    val cachedTeacherPlans: Flow<Map<String, ParsedPlan>> =
        dao.observePlansByCategory(PlanCategory.TEACHER.name).map { entities ->
            entities.mapNotNull { entity ->
                runCatching { entity.href to PlanJsonSerializer.fromJson(entity.payloadJson) }.getOrNull()
            }.toMap()
        }

    val scheduleChanges: Flow<List<ScheduleChangeEntity>> = dao.observeScheduleChanges()
    val unreadChangesCount: Flow<Int> = dao.observeUnreadChangesCount()

    val directorFreeDays: Flow<List<HolidayEvent>> = dao.observeDirectorFreeDays().map { list ->
        list.map { entity ->
            HolidayEvent(
                date = PolishCalendarCalculator.fromEpochDay(entity.epochDay),
                name = entity.name,
                isDirectorDay = true,
                directorDayId = entity.id
            )
        }
    }

    fun observePlan(href: String): Flow<ParsedPlan?> = dao.observePlan(href).map { entity ->
        entity?.let { runCatching { PlanJsonSerializer.fromJson(it.payloadJson) }.getOrNull() }
    }

    suspend fun getCachedPlanOnce(href: String): ParsedPlan? {
        val entity = dao.getPlanOnce(href) ?: return null
        return runCatching { PlanJsonSerializer.fromJson(entity.payloadJson) }.getOrNull()
    }

    suspend fun syncIndex(): Result<List<PlanEntry>> {
        val result = remote.fetchIndex()
        result.onSuccess { entries ->
            val entities = entries.mapIndexed { idx, entry ->
                PlanIndexEntity(
                    href = entry.href,
                    name = entry.name,
                    category = entry.category.name,
                    sortOrder = idx
                )
            }
            dao.replaceIndex(entities)
        }
        return result
    }

    suspend fun syncPlanAndDetectChanges(
        entry: PlanEntry,
        recordChanges: Boolean = true
    ): Result<Pair<ParsedPlan, List<ScheduleChangeEntity>>> {
        val previous = getCachedPlanOnce(entry.href)
        val remoteResult = remote.fetchPlan(entry)
        return remoteResult.map { fresh ->
            val changes = if (recordChanges && previous != null && previous.contentHash != fresh.contentHash) {
                computeScheduleDiff(previous, fresh)
            } else {
                emptyList()
            }

            if (changes.isNotEmpty()) {
                dao.insertScheduleChanges(changes)
            }

            dao.upsertPlan(
                CachedPlanEntity(
                    href = fresh.href,
                    title = fresh.title,
                    category = fresh.category.name,
                    generatedAt = fresh.generatedAt,
                    contentHash = fresh.contentHash,
                    payloadJson = PlanJsonSerializer.toJson(fresh),
                    fetchedAtEpochMs = fresh.fetchedAtEpochMs
                )
            )
            fresh to changes
        }
    }

    suspend fun syncAllTeachers(): Result<Int> {
        var index = dao.getIndexOnce()
            .filter { it.category == PlanCategory.TEACHER.name }
            .map { PlanEntry(it.href, it.name, PlanCategory.TEACHER) }

        if (index.isEmpty()) {
            val idxResult = syncIndex()
            if (idxResult.isFailure) {
                return Result.failure(idxResult.exceptionOrNull() ?: Exception("Błąd pobierania indeksu"))
            }
            index = idxResult.getOrNull()
                ?.filter { it.category == PlanCategory.TEACHER }
                .orEmpty()
        }

        if (index.isEmpty()) return Result.success(0)

        val fetched = remote.fetchPlansBatch(index)
        if (fetched.isEmpty()) {
            return Result.failure(Exception("Nie udało się pobrać planów nauczycieli z serwera."))
        }

        val entities = fetched.map { plan ->
            CachedPlanEntity(
                href = plan.href,
                title = plan.title,
                category = plan.category.name,
                generatedAt = plan.generatedAt,
                contentHash = plan.contentHash,
                payloadJson = PlanJsonSerializer.toJson(plan),
                fetchedAtEpochMs = plan.fetchedAtEpochMs
            )
        }
        dao.upsertPlans(entities)
        return Result.success(entities.size)
    }

    suspend fun markAllChangesRead() = dao.markAllChangesRead()

    suspend fun clearScheduleChanges() = dao.clearScheduleChanges()

    suspend fun clearCachedPlans() = dao.clearCachedPlans()

    suspend fun addDirectorFreeDay(date: SimpleDate, name: String) {
        val epochDay = PolishCalendarCalculator.toEpochDay(date)
        dao.insertDirectorFreeDay(
            DirectorFreeDayEntity(
                epochDay = epochDay,
                name = name.ifBlank { "Dzień dyrektorski" }
            )
        )
    }

    suspend fun removeDirectorFreeDay(id: Long) {
        dao.deleteDirectorFreeDay(id)
    }

    companion object {
        private val DAY_NAMES = mapOf(
            1 to "Poniedziałek",
            2 to "Wtorek",
            3 to "Środa",
            4 to "Czwartek",
            5 to "Piątek",
            6 to "Sobota"
        )

        fun computeScheduleDiff(oldPlan: ParsedPlan, newPlan: ParsedPlan): List<ScheduleChangeEntity> {
            val now = System.currentTimeMillis()
            val oldMap = oldPlan.lessons.associateBy { it.dayOfWeek to it.lessonNumber }
            val newMap = newPlan.lessons.associateBy { it.dayOfWeek to it.lessonNumber }
            val allKeys = (oldMap.keys + newMap.keys).sortedWith(compareBy({ it.first }, { it.second }))

            val changes = mutableListOf<ScheduleChangeEntity>()
            for ((day, lessonNr) in allKeys) {
                val oldCell = oldMap[day to lessonNr]
                val newCell = newMap[day to lessonNr]
                val dayLabel = DAY_NAMES[day] ?: "Dzień $day"

                when {
                    oldCell == null && newCell != null -> {
                        val desc = formatCellSummary(newCell)
                        changes.add(
                            ScheduleChangeEntity(
                                planHref = newPlan.href,
                                planTitle = newPlan.title,
                                summary = "$dayLabel, lekcja $lessonNr: dodano $desc",
                                detectedAtEpochMs = now
                            )
                        )
                    }
                    oldCell != null && newCell == null -> {
                        val desc = formatCellSummary(oldCell)
                        changes.add(
                            ScheduleChangeEntity(
                                planHref = newPlan.href,
                                planTitle = newPlan.title,
                                summary = "$dayLabel, lekcja $lessonNr: odwołano $desc",
                                detectedAtEpochMs = now
                            )
                        )
                    }
                    oldCell != null && newCell != null -> {
                        val oldSummary = formatCellSummary(oldCell)
                        val newSummary = formatCellSummary(newCell)
                        if (oldSummary != newSummary) {
                            changes.add(
                                ScheduleChangeEntity(
                                    planHref = newPlan.href,
                                    planTitle = newPlan.title,
                                    summary = "$dayLabel, lekcja $lessonNr: zmiana z [$oldSummary] na [$newSummary]",
                                    detectedAtEpochMs = now
                                )
                            )
                        }
                    }
                }
            }
            return changes
        }

        private fun formatCellSummary(cell: LessonCell): String {
            return cell.subgroups.joinToString(" / ") { sg ->
                buildString {
                    append(sg.subject)
                    sg.roomName?.let { append(" (s. $it)") }
                    sg.teacherName?.let { append(" [$it]") }
                }
            }
        }

        fun buildTeacherLocations(
            teachers: List<PlanEntry>,
            teacherPlans: Map<String, ParsedPlan>,
            dayOfWeek: Int,
            targetMinutes: Int?,
            targetLessonNumber: Int?
        ): List<TeacherLocationStatus> {
            return teachers.map { teacher ->
                val plan = teacherPlans[teacher.href]
                if (plan == null) {
                    TeacherLocationStatus(
                        teacher = teacher,
                        isCurrentlyTeaching = false,
                        dayOfWeek = dayOfWeek,
                        hasCachedPlan = false
                    )
                } else {
                    val todayLessons = plan.lessons
                        .filter { it.dayOfWeek == dayOfWeek }
                        .sortedBy { it.lessonNumber }

                    val activeCell = when {
                        targetLessonNumber != null -> todayLessons.firstOrNull { it.lessonNumber == targetLessonNumber }
                        targetMinutes != null -> todayLessons.firstOrNull {
                            targetMinutes in it.startMinutes until it.endMinutes
                        }
                        else -> null
                    }

                    val nextCell = when {
                        targetLessonNumber != null -> todayLessons.firstOrNull { it.lessonNumber > targetLessonNumber }
                        targetMinutes != null -> todayLessons.firstOrNull { it.startMinutes > targetMinutes }
                        else -> todayLessons.firstOrNull()
                    }

                    val nextSlotAndLesson = nextCell?.let { cell ->
                        val slot = plan.bellSlots.firstOrNull { it.lessonNumber == cell.lessonNumber }
                            ?: com.example.model.BellSlot(
                                lessonNumber = cell.lessonNumber,
                                timeRange = cell.timeRange,
                                startMinutes = cell.startMinutes,
                                endMinutes = cell.endMinutes
                            )
                        cell.subgroups.firstOrNull()?.let { slot to it }
                    }

                    val minutesLeft = if (activeCell != null && targetMinutes != null) {
                        (activeCell.endMinutes - targetMinutes).coerceAtLeast(0)
                    } else null

                    TeacherLocationStatus(
                        teacher = teacher,
                        isCurrentlyTeaching = activeCell != null,
                        currentLesson = activeCell?.subgroups?.firstOrNull(),
                        currentTimeRange = activeCell?.timeRange,
                        minutesRemaining = minutesLeft,
                        nextLessonToday = nextSlotAndLesson,
                        dayOfWeek = dayOfWeek,
                        hasCachedPlan = true
                    )
                }
            }
        }
    }
}
