package com.example.data.network

import com.example.model.BellSlot
import com.example.model.LessonCell
import com.example.model.LessonSubgroup
import com.example.model.ParsedPlan
import com.example.model.PlanCategory
import com.example.model.PlanEntry
import java.nio.charset.Charset
import java.security.MessageDigest

/**
 * Parses Vulcan Optivum HTML timetable files served at http://zs5rybnik.pl/_plan/
 */
object OptivumHtmlParser {

    private val anchorRegex = Regex(
        """<a\b([^>]*)>(.*?)</a>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val hrefAttrRegex = Regex(
        """href\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )
    private val classAttrRegex = Regex(
        """class\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )
    private val titleSpanRegex = Regex(
        """<span\b[^>]*class\s*=\s*["'][^"']*tytulnapis[^"']*["'][^>]*>(.*?)</span>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val htmlTitleRegex = Regex(
        """<title>(.*?)</title>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val generatedTdRegex = Regex(
        """<td\b[^>]*class\s*=\s*["'][^"']*op[^"']*["'][^>]*>(.*?)</td>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val trRegex = Regex(
        """<tr\b[^>]*>(.*?)</tr>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val tdRegex = Regex(
        """<td\b([^>]*)>(.*?)</td>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val tableRegex = Regex(
        """<table\b([^>]*)>(.*?)</table>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val spanOrARegex = Regex(
        """<(span|a)\b([^>]*)>(.*?)</\1>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val brSplitRegex = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val tagStripRegex = Regex("""<[^>]+>""")
    private val metaCharsetRegex = Regex(
        """charset\s*=\s*["']?([A-Za-z0-9\-_]+)""",
        RegexOption.IGNORE_CASE
    )
    private val frameSrcRegex = Regex(
        """<frame\b[^>]*src\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )

    fun decodeHtmlBytes(bytes: ByteArray, contentTypeHeader: String? = null): String {
        val headerCharset = contentTypeHeader?.let { metaCharsetRegex.find(it)?.groupValues?.get(1) }
        val headPreview = String(bytes, 0, minOf(bytes.size, 2048), Charsets.ISO_8859_1)
        val metaCharset = metaCharsetRegex.find(headPreview)?.groupValues?.get(1)
        val charsetName = (headerCharset ?: metaCharset ?: "UTF-8").uppercase()

        val resolvedCharset = when {
            charsetName.contains("1250") -> Charset.forName("windows-1250")
            charsetName.contains("8859-2") || charsetName.contains("LATIN2") -> Charset.forName("ISO-8859-2")
            else -> Charsets.UTF_8
        }
        return String(bytes, resolvedCharset)
    }

    fun extractListFrameHref(indexHtml: String): String? {
        return frameSrcRegex.findAll(indexHtml)
            .map { it.groupValues[1].trim() }
            .firstOrNull { src ->
                val lower = src.lowercase()
                lower.contains("lista") || (!lower.contains("o1") && !lower.contains("logo"))
            }
    }

    fun parseIndex(html: String): List<PlanEntry> {
        val seen = linkedSetOf<String>()
        val entries = mutableListOf<PlanEntry>()

        for (match in anchorRegex.findAll(html)) {
            val attrs = match.groupValues[1]
            val rawHref = hrefAttrRegex.find(attrs)?.groupValues?.get(1)?.trim() ?: continue
            val normalizedHref = normalizeHref(rawHref)
            val filename = normalizedHref.substringAfterLast('/')
            val category = when {
                filename.startsWith("o") && filename.endsWith(".html") -> PlanCategory.CLASS
                filename.startsWith("n") && filename.endsWith(".html") -> PlanCategory.TEACHER
                filename.startsWith("s") && filename.endsWith(".html") -> PlanCategory.ROOM
                else -> null
            } ?: continue

            val label = cleanText(match.groupValues[2])
            if (label.isBlank()) continue
            if (seen.add(normalizedHref)) {
                entries.add(PlanEntry(href = normalizedHref, name = label, category = category))
            }
        }
        return entries
    }

    fun parsePlanPage(html: String, entry: PlanEntry): ParsedPlan {
        val title = titleSpanRegex.find(html)?.groupValues?.get(1)?.let(::cleanText)
            ?.takeIf { it.isNotBlank() }
            ?: htmlTitleRegex.find(html)?.groupValues?.get(1)?.let(::cleanText)
                ?.removePrefix("Plan lekcji - ")?.trim()
                ?.takeIf { it.isNotBlank() }
            ?: entry.name

        val generatedAt = generatedTdRegex.findAll(html)
            .map { cleanText(it.groupValues[1]) }
            .firstOrNull { it.isNotBlank() && (it.contains("Wygenerowano", true) || it.contains("Obowiązuje", true) || it.any(Char::isDigit)) }
            ?: ""

        // Locate main timetable table (<table class="tabela"> or table containing <td class="nr">)
        val timetableHtml = tableRegex.findAll(html)
            .firstOrNull { tbl ->
                val attrs = tbl.groupValues[1]
                val body = tbl.groupValues[2]
                classAttrRegex.find(attrs)?.groupValues?.get(1)?.contains("tabela", true) == true ||
                    body.contains("""class="nr"""", true) ||
                    body.contains("""class='nr'""", true)
            }?.groupValues?.get(2) ?: html

        val bellSlots = mutableListOf<BellSlot>()
        val lessons = mutableListOf<LessonCell>()

        for (rowMatch in trRegex.findAll(timetableHtml)) {
            val rowHtml = rowMatch.groupValues[1]
            val cells = tdRegex.findAll(rowHtml).toList()
            if (cells.isEmpty()) continue

            var nr: Int? = null
            var timeRange = ""
            val dayCellsHtml = mutableListOf<String>()

            for (cell in cells) {
                val attrs = cell.groupValues[1]
                val cellClass = classAttrRegex.find(attrs)?.groupValues?.get(1)?.lowercase() ?: ""
                val content = cell.groupValues[2]

                when {
                    cellClass.split(" ").contains("nr") -> {
                        nr = cleanText(content).toIntOrNull()
                    }
                    cellClass.split(" ").contains("g") -> {
                        timeRange = cleanText(content)
                    }
                    cellClass.split(" ").contains("l") -> {
                        dayCellsHtml.add(content)
                    }
                    nr != null && timeRange.isNotBlank() -> {
                        // Fallback if class="l" wasn't explicitly set
                        dayCellsHtml.add(content)
                    }
                }
            }

            if (nr != null && timeRange.isNotBlank()) {
                val (startMin, endMin) = parseTimeRange(timeRange)
                bellSlots.add(
                    BellSlot(
                        lessonNumber = nr,
                        timeRange = normalizeTimeRange(timeRange),
                        startMinutes = startMin,
                        endMinutes = endMin
                    )
                )

                dayCellsHtml.forEachIndexed { index, cellContent ->
                    val dayOfWeek = index + 1 // 1=Mon .. 5=Fri
                    if (dayOfWeek in 1..6) {
                        val subgroups = parseLessonCellSubgroups(cellContent, entry)
                        if (subgroups.isNotEmpty()) {
                            lessons.add(
                                LessonCell(
                                    lessonNumber = nr,
                                    timeRange = normalizeTimeRange(timeRange),
                                    startMinutes = startMin,
                                    endMinutes = endMin,
                                    dayOfWeek = dayOfWeek,
                                    subgroups = subgroups
                                )
                            )
                        }
                    }
                }
            }
        }

        val normalizedSignature = buildString {
            append(title).append('|')
            lessons.sortedWith(compareBy({ it.dayOfWeek }, { it.lessonNumber })).forEach { cell ->
                append(cell.dayOfWeek).append(':').append(cell.lessonNumber).append(':')
                cell.subgroups.forEach { sg ->
                    append(sg.subject).append('/')
                        .append(sg.teacherName.orEmpty()).append('/')
                        .append(sg.roomName.orEmpty()).append('/')
                        .append(sg.className.orEmpty()).append(';')
                }
                append('|')
            }
        }

        return ParsedPlan(
            href = entry.href,
            title = title,
            category = entry.category,
            generatedAt = generatedAt,
            bellSlots = bellSlots.distinctBy { it.lessonNumber }.sortedBy { it.lessonNumber },
            lessons = lessons,
            contentHash = sha256(normalizedSignature)
        )
    }

    private fun parseLessonCellSubgroups(cellHtml: String, ownerEntry: PlanEntry): List<LessonSubgroup> {
        val trimmed = cleanText(cellHtml)
        if (trimmed.isBlank() || trimmed == "-") return emptyList()

        val rawSegments = brSplitRegex.split(cellHtml)
            .map { it.trim() }
            .filter { cleanText(it).isNotBlank() && cleanText(it) != "-" }

        val parsed = mutableListOf<LessonSubgroup>()
        for (segment in rawSegments) {
            val subjectParts = mutableListOf<String>()
            var teacherName: String? = null
            var teacherHref: String? = null
            var roomName: String? = null
            var roomHref: String? = null
            val classNames = mutableListOf<String>()
            var firstClassHref: String? = null

            for (tag in spanOrARegex.findAll(segment)) {
                val attrs = tag.groupValues[2]
                val inner = cleanText(tag.groupValues[3])
                if (inner.isBlank()) continue
                val classes = classAttrRegex.find(attrs)?.groupValues?.get(1)?.lowercase()?.split(" ") ?: emptyList()
                val href = hrefAttrRegex.find(attrs)?.groupValues?.get(1)?.let(::normalizeHref)

                when {
                    "p" in classes -> subjectParts.add(inner)
                    "n" in classes -> {
                        teacherName = inner
                        if (href != null) teacherHref = href
                    }
                    "s" in classes -> {
                        roomName = inner
                        if (href != null) roomHref = href
                    }
                    "o" in classes -> {
                        classNames.add(inner)
                        if (firstClassHref == null && href != null) firstClassHref = href
                    }
                }
            }

            // If a teacher plan doesn't link the teacher explicitly, fill owner as teacher
            if (ownerEntry.category == PlanCategory.TEACHER && teacherName == null) {
                teacherName = ownerEntry.name
                teacherHref = ownerEntry.href
            }
            if (ownerEntry.category == PlanCategory.ROOM && roomName == null) {
                roomName = ownerEntry.name
                roomHref = ownerEntry.href
            }
            if (ownerEntry.category == PlanCategory.CLASS && classNames.isEmpty()) {
                classNames.add(ownerEntry.name)
                firstClassHref = ownerEntry.href
            }

            val subjectRaw = if (subjectParts.isNotEmpty()) {
                subjectParts.joinToString(" ")
            } else {
                // Fallback: strip matched tokens or raw text
                cleanText(segment)
            }

            // Extract optional group suffix like "-1/2" or "(gr. 1)"
            val groupMatch = Regex("""-(\d+/\d+)$""").find(subjectRaw)
            val groupLabel = groupMatch?.groupValues?.get(1)

            if (subjectRaw.isNotBlank()) {
                parsed.add(
                    LessonSubgroup(
                        subject = subjectRaw,
                        teacherName = teacherName,
                        teacherHref = teacherHref,
                        roomName = roomName,
                        roomHref = roomHref,
                        className = classNames.takeIf { it.isNotEmpty() }?.joinToString(", "),
                        classHref = firstClassHref,
                        groupLabel = groupLabel
                    )
                )
            }
        }
        return parsed
    }

    fun normalizeHref(raw: String): String {
        val cleaned = raw.trim().removePrefix("./").removePrefix("/")
        return when {
            cleaned.startsWith("plany/") -> cleaned
            cleaned.matches(Regex("""[ons]\d+\.html""", RegexOption.IGNORE_CASE)) -> "plany/$cleaned"
            else -> cleaned
        }
    }

    fun parseTimeRange(range: String): Pair<Int, Int> {
        val matches = Regex("""(\d{1,2}):(\d{2})""").findAll(range).toList()
        if (matches.size >= 2) {
            val sH = matches[0].groupValues[1].toIntOrNull() ?: 8
            val sM = matches[0].groupValues[2].toIntOrNull() ?: 0
            val eH = matches[1].groupValues[1].toIntOrNull() ?: 8
            val eM = matches[1].groupValues[2].toIntOrNull() ?: 45
            return (sH * 60 + sM) to (eH * 60 + eM)
        }
        return 0 to 0
    }

    private fun normalizeTimeRange(range: String): String {
        val matches = Regex("""(\d{1,2}):(\d{2})""").findAll(range).toList()
        return if (matches.size >= 2) {
            val s = "${matches[0].groupValues[1]}:${matches[0].groupValues[2]}"
            val e = "${matches[1].groupValues[1]}:${matches[1].groupValues[2]}"
            "$s – $e"
        } else {
            range.trim()
        }
    }

    fun cleanText(html: String): String {
        return tagStripRegex.replace(html, " ")
            .replace("&nbsp;", " ")
            .replace("&#160;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&oacute;", "ó")
            .replace("&Oacute;", "Ó")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
