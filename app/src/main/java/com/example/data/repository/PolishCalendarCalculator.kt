package com.example.data.repository

import com.example.model.HolidayCountdownInfo
import com.example.model.HolidayEvent
import com.example.model.SimpleDate
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object PolishCalendarCalculator {

    fun today(): SimpleDate {
        val cal = Calendar.getInstance()
        return SimpleDate(
            year = cal.get(Calendar.YEAR),
            month = cal.get(Calendar.MONTH) + 1,
            day = cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun fromEpochMillis(millis: Long): SimpleDate {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return SimpleDate(
            year = cal.get(Calendar.YEAR),
            month = cal.get(Calendar.MONTH) + 1,
            day = cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    private val utcTimeZone: TimeZone = TimeZone.getTimeZone("UTC")

    fun toEpochDay(date: SimpleDate): Long {
        val cal = Calendar.getInstance(utcTimeZone).apply {
            set(Calendar.YEAR, date.year)
            set(Calendar.MONTH, date.month - 1)
            set(Calendar.DAY_OF_MONTH, date.day)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return TimeUnit.MILLISECONDS.toDays(cal.timeInMillis)
    }

    fun fromEpochDay(epochDay: Long): SimpleDate {
        val cal = Calendar.getInstance(utcTimeZone).apply {
            timeInMillis = TimeUnit.DAYS.toMillis(epochDay)
        }
        return SimpleDate(
            year = cal.get(Calendar.YEAR),
            month = cal.get(Calendar.MONTH) + 1,
            day = cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun dayOfWeekIso(date: SimpleDate): Int {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, date.year)
            set(Calendar.MONTH, date.month - 1)
            set(Calendar.DAY_OF_MONTH, date.day)
        }
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
    }

    fun addDays(date: SimpleDate, days: Int): SimpleDate {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, date.year)
            set(Calendar.MONTH, date.month - 1)
            set(Calendar.DAY_OF_MONTH, date.day)
            add(Calendar.DAY_OF_MONTH, days)
        }
        return SimpleDate(
            year = cal.get(Calendar.YEAR),
            month = cal.get(Calendar.MONTH) + 1,
            day = cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun daysBetween(start: SimpleDate, end: SimpleDate): Long {
        return toEpochDay(end) - toEpochDay(start)
    }

    fun expandDateRange(
        start: SimpleDate,
        end: SimpleDate,
        maxDays: Int = 60
    ): List<SimpleDate> {
        val (from, to) = if (start <= end) start to end else end to start
        val result = mutableListOf<SimpleDate>()
        var cursor = from
        while (cursor <= to && result.size < maxDays) {
            result.add(cursor)
            cursor = addDays(cursor, 1)
        }
        return result
    }

    /**
     * Anonymous Gregorian Easter algorithm (Meeus/Jones/Butcher).
     */
    fun easterSunday(year: Int): SimpleDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return SimpleDate(year, month, day)
    }

    fun polishPublicHolidays(year: Int): List<HolidayEvent> {
        val easter = easterSunday(year)
        val easterMonday = addDays(easter, 1)
        val pentecost = addDays(easter, 49)
        val corpusChristi = addDays(easter, 60)

        val list = mutableListOf(
            HolidayEvent(SimpleDate(year, 1, 1), "Nowy Rok"),
            HolidayEvent(SimpleDate(year, 1, 6), "Święto Trzech Króli"),
            HolidayEvent(easter, "Wielkanoc"),
            HolidayEvent(easterMonday, "Poniedziałek Wielkanocny"),
            HolidayEvent(SimpleDate(year, 5, 1), "Święto Pracy"),
            HolidayEvent(SimpleDate(year, 5, 3), "Święto Konstytucji 3 Maja"),
            HolidayEvent(pentecost, "Zielone Świątki"),
            HolidayEvent(corpusChristi, "Boże Ciało"),
            HolidayEvent(SimpleDate(year, 8, 15), "Wniebowzięcie NMP"),
            HolidayEvent(SimpleDate(year, 11, 1), "Wszystkich Świętych"),
            HolidayEvent(SimpleDate(year, 11, 11), "Narodowe Święto Niepodległości"),
            HolidayEvent(SimpleDate(year, 12, 25), "Boże Narodzenie (1. dzień)"),
            HolidayEvent(SimpleDate(year, 12, 26), "Boże Narodzenie (2. dzień)")
        )
        // Od 2025 roku Wigilia (24 grudnia) jest dniem ustawowo wolnym od pracy w Polsce
        if (year >= 2025) {
            list.add(HolidayEvent(SimpleDate(year, 12, 24), "Wigilia Bożego Narodzenia"))
        }
        return list.sortedBy { it.date }
    }

    /**
     * In Poland, zakończenie zajęć dydaktyczno-wychowawczych (start of summer holidays)
     * falls on the first Friday after June 20 (so the first day of wakacje is the Saturday right after,
     * or we count down to the official zakończenie roku szkolnego Friday!).
     */
    fun endOfSchoolYearFriday(year: Int): SimpleDate {
        val june20 = SimpleDate(year, 6, 20)
        val dow = dayOfWeekIso(june20) // Mon=1 .. Fri=5 .. Sun=7
        val daysToAdd = if (dow < 5) 5 - dow else (7 - dow) + 5
        return addDays(june20, daysToAdd)
    }

    fun calculateCountdown(
        referenceDate: SimpleDate,
        directorDays: List<HolidayEvent>
    ): HolidayCountdownInfo {
        val yearsToCheck = listOf(referenceDate.year, referenceDate.year + 1)
        val publicHolidays = yearsToCheck.flatMap { polishPublicHolidays(it) }

        val mergedByDate = linkedMapOf<SimpleDate, HolidayEvent>()
        for (h in publicHolidays) {
            mergedByDate[h.date] = h
        }
        for (d in directorDays) {
            // Director day overrides or merges cleanly
            val existing = mergedByDate[d.date]
            mergedByDate[d.date] = if (existing != null) {
                existing.copy(
                    name = "${existing.name} / ${d.name}",
                    isDirectorDay = true,
                    directorDayId = d.directorDayId
                )
            } else {
                d
            }
        }

        val allEvents = mergedByDate.values.sortedBy { it.date }
        val todayEvent = allEvents.firstOrNull { it.date == referenceDate }
        val dow = dayOfWeekIso(referenceDate)
        val isWeekend = dow == 6 || dow == 7

        val upcoming = allEvents.filter { it.date > referenceDate }
        val nextFree = upcoming.firstOrNull()
        val daysUntilNextFree = if (nextFree != null) daysBetween(referenceDate, nextFree.date) else 0L

        val currentYearEnd = endOfSchoolYearFriday(referenceDate.year)
        val sept1 = SimpleDate(referenceDate.year, 9, 1)
        val isSummerBreak = referenceDate > currentYearEnd && referenceDate < sept1

        val targetSummer = if (referenceDate <= currentYearEnd) {
            currentYearEnd
        } else {
            endOfSchoolYearFriday(referenceDate.year + 1)
        }
        val daysUntilSummer = daysBetween(referenceDate, targetSummer).coerceAtLeast(0L)

        // Count actual school days remaining (Mon-Fri excluding holidays and director days)
        val freeDateSet = allEvents.map { it.date }.toHashSet()
        var schoolDays = 0
        if (!isSummerBreak && referenceDate < targetSummer) {
            var cursor = addDays(referenceDate, 1)
            while (cursor <= targetSummer) {
                val cDow = dayOfWeekIso(cursor)
                if (cDow in 1..5 && cursor !in freeDateSet) {
                    schoolDays++
                }
                cursor = addDays(cursor, 1)
            }
        }

        return HolidayCountdownInfo(
            referenceDate = referenceDate,
            todayFreeEvent = todayEvent,
            isWeekend = isWeekend,
            nextFreeEvent = nextFree,
            daysUntilNextFreeDay = daysUntilNextFree,
            summerVacationDate = targetSummer,
            daysUntilSummer = daysUntilSummer,
            schoolDaysUntilSummer = schoolDays,
            isCurrentlySummerBreak = isSummerBreak,
            upcomingEvents = upcoming.take(16)
        )
    }

    fun formatDatePl(date: SimpleDate): String {
        val monthName = when (date.month) {
            1 -> "stycznia"
            2 -> "lutego"
            3 -> "marca"
            4 -> "kwietnia"
            5 -> "maja"
            6 -> "czerwca"
            7 -> "lipca"
            8 -> "sierpnia"
            9 -> "września"
            10 -> "października"
            11 -> "listopada"
            12 -> "grudnia"
            else -> ""
        }
        val dowName = when (dayOfWeekIso(date)) {
            1 -> "poniedziałek"
            2 -> "wtorek"
            3 -> "środa"
            4 -> "czwartek"
            5 -> "piątek"
            6 -> "sobota"
            7 -> "niedziela"
            else -> ""
        }
        return "${date.day} $monthName ${date.year} ($dowName)"
    }

    fun formatShortDate(date: SimpleDate): String {
        val d = date.day.toString().padStart(2, '0')
        val m = date.month.toString().padStart(2, '0')
        return "$d.$m.${date.year}"
    }
}
