package com.example.model

enum class PlanCategory(val labelPl: String, val prefix: String) {
    CLASS("Klasy", "o"),
    TEACHER("Nauczyciele", "n"),
    ROOM("Sale", "s")
}

data class PlanEntry(
    val href: String, // e.g. "plany/o1.html"
    val name: String, // e.g. "1Ti", "J. Kowalski (JK)", "104"
    val category: PlanCategory
)

data class BellSlot(
    val lessonNumber: Int,
    val timeRange: String,
    val startMinutes: Int,
    val endMinutes: Int
)

data class LessonSubgroup(
    val subject: String,
    val teacherName: String? = null,
    val teacherHref: String? = null,
    val roomName: String? = null,
    val roomHref: String? = null,
    val className: String? = null,
    val classHref: String? = null,
    val groupLabel: String? = null
)

data class LessonCell(
    val lessonNumber: Int,
    val timeRange: String,
    val startMinutes: Int,
    val endMinutes: Int,
    val dayOfWeek: Int, // 1 = Poniedziałek .. 5 = Piątek
    val subgroups: List<LessonSubgroup>
)

data class ParsedPlan(
    val href: String,
    val title: String,
    val category: PlanCategory,
    val generatedAt: String,
    val bellSlots: List<BellSlot>,
    val lessons: List<LessonCell>,
    val contentHash: String,
    val fetchedAtEpochMs: Long = System.currentTimeMillis()
)

data class TeacherLocationStatus(
    val teacher: PlanEntry,
    val isCurrentlyTeaching: Boolean,
    val currentLesson: LessonSubgroup? = null,
    val currentTimeRange: String? = null,
    val minutesRemaining: Int? = null,
    val nextLessonToday: Pair<BellSlot, LessonSubgroup>? = null,
    val dayOfWeek: Int,
    val hasCachedPlan: Boolean = true
)

enum class ThemeMode(val labelPl: String) {
    SYSTEM("Systemowy"),
    LIGHT("Jasny"),
    DARK("Ciemny")
}

data class SimpleDate(
    val year: Int,
    val month: Int, // 1..12
    val day: Int    // 1..31
) : Comparable<SimpleDate> {
    override fun compareTo(other: SimpleDate): Int {
        if (year != other.year) return year.compareTo(other.year)
        if (month != other.month) return month.compareTo(other.month)
        return day.compareTo(other.day)
    }
}

data class HolidayEvent(
    val date: SimpleDate,
    val name: String,
    val isDirectorDay: Boolean = false,
    val directorDayId: Long? = null
)

data class HolidayCountdownInfo(
    val referenceDate: SimpleDate,
    val todayFreeEvent: HolidayEvent?,
    val isWeekend: Boolean,
    val nextFreeEvent: HolidayEvent?,
    val daysUntilNextFreeDay: Long,
    val summerVacationDate: SimpleDate,
    val daysUntilSummer: Long,
    val schoolDaysUntilSummer: Int,
    val isCurrentlySummerBreak: Boolean,
    val upcomingEvents: List<HolidayEvent>
)
