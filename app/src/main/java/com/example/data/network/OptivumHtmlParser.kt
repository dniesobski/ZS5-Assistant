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
    private val spanOrATagRegex = Regex(
        """<(/?)(span|a)\b([^>]*)>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val classGroupSuffixRegex = Regex(
        """^(-\s*(?:\d+/\d+|gr\.?\s*\d+|[A-Za-zżółćęśąźńŻÓŁĆĘŚĄŹŃ0-9]{1,5}\d?)|\s*\((?:\d+/\d+|gr\.?\s*\d+|[A-Za-z0-9]{1,4})\)|\s+\d+/\d+)(?=$|[\s,;(<])""",
        RegexOption.IGNORE_CASE
    )
    private val untaggedClassTokenRegex = Regex(
        """\b(\d+[A-Za-zżółćęśąźńŻÓŁĆĘŚĄŹŃ]{1,5}\d*(?:-\d+/\d+|/\d+|-gr\.?\s*\d+|-[A-Za-z]{1,3}\d?)?)\b"""
    )
    private val leadingClassInSubjectRegex = Regex(
        """^(\d+[A-Za-zżółćęśąźńŻÓŁĆĘŚĄŹŃ]{1,5}\d*-\d+/\d+)\s+(.+)$"""
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

    private enum class TagRole { NONE, SUBJECT, TEACHER, ROOM, CLASS }

    private data class OpenTag(
        val tagName: String,
        val role: TagRole,
        val href: String?
    )

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
            var extractedGroupLabel: String? = null
            val untaggedParts = mutableListOf<String>()

            val tagStack = ArrayDeque<OpenTag>()
            var activeRole = TagRole.NONE
            val roleBuffer = StringBuilder()
            var activeHref: String? = null
            var justClosedClass = false

            fun currentEffectiveTag(): OpenTag? = tagStack.lastOrNull { it.role != TagRole.NONE }

            fun flushRoleBuffer() {
                val text = cleanText(roleBuffer.toString())
                roleBuffer.clear()
                if (text.isBlank()) {
                    activeHref = null
                    return
                }
                when (activeRole) {
                    TagRole.SUBJECT -> {
                        subjectParts.add(text)
                        justClosedClass = false
                    }
                    TagRole.TEACHER -> {
                        teacherName = if (teacherName == null) text else "$teacherName, $text"
                        if (activeHref != null && teacherHref == null) teacherHref = activeHref
                        justClosedClass = false
                    }
                    TagRole.ROOM -> {
                        roomName = text
                        if (activeHref != null) roomHref = activeHref
                        justClosedClass = false
                    }
                    TagRole.CLASS -> {
                        classNames.add(text)
                        if (firstClassHref == null && activeHref != null) firstClassHref = activeHref
                        justClosedClass = true
                    }
                    TagRole.NONE -> Unit
                }
                activeHref = null
            }

            fun handlePlainChunk(rawChunk: String) {
                if (rawChunk.isEmpty()) return
                if (activeRole != TagRole.NONE) {
                    roleBuffer.append(rawChunk)
                    return
                }
                var remaining = rawChunk
                if (justClosedClass && classNames.isNotEmpty()) {
                    val suffixMatch = classGroupSuffixRegex.find(remaining)
                    if (suffixMatch != null) {
                        val suffixClean = cleanText(suffixMatch.groupValues[1])
                        if (suffixClean.isNotBlank()) {
                            val lastIdx = classNames.lastIndex
                            val combined = if (suffixClean.startsWith("-") || suffixClean.startsWith("(")) {
                                "${classNames[lastIdx]}$suffixClean"
                            } else {
                                "${classNames[lastIdx]}-$suffixClean"
                            }
                            classNames[lastIdx] = combined
                            Regex("""(\d+/\d+)""").find(suffixClean)?.let {
                                extractedGroupLabel = it.groupValues[1]
                            }
                        }
                        remaining = remaining.substring(suffixMatch.range.last + 1)
                    }
                    justClosedClass = false
                }

                val cleanedRemaining = cleanText(remaining)
                if (cleanedRemaining.isNotBlank() && cleanedRemaining != "," && cleanedRemaining != "-") {
                    if (ownerEntry.category != PlanCategory.CLASS) {
                        var leftover = cleanedRemaining
                        val classTokens = untaggedClassTokenRegex.findAll(cleanedRemaining).map { it.groupValues[1] }.toList()
                        if (classTokens.isNotEmpty() && subjectParts.isEmpty()) {
                            classTokens.forEach { tok ->
                                if (tok !in classNames) classNames.add(tok)
                                Regex("""(\d+/\d+)""").find(tok)?.let {
                                    extractedGroupLabel = it.groupValues[1]
                                }
                                leftover = leftover.replaceFirst(tok, "").trim().trim(',', ' ')
                            }
                        }
                        if (leftover.isNotBlank() && leftover != "," && leftover != "-") {
                            untaggedParts.add(leftover)
                        }
                    } else {
                        untaggedParts.add(cleanedRemaining)
                    }
                }
            }

            var cursor = 0
            for (tagMatch in spanOrATagRegex.findAll(segment)) {
                val matchStart = tagMatch.range.first
                if (matchStart > cursor) {
                    handlePlainChunk(segment.substring(cursor, matchStart))
                }
                cursor = tagMatch.range.last + 1

                val isClosing = tagMatch.groupValues[1] == "/"
                val tagName = tagMatch.groupValues[2].lowercase()
                val attrs = tagMatch.groupValues[3]

                if (!isClosing) {
                    val classes = classAttrRegex.find(attrs)?.groupValues?.get(1)?.lowercase()
                        ?.split(Regex("""\s+"""))
                        ?.filter { it.isNotBlank() }
                        ?: emptyList()
                    val href = hrefAttrRegex.find(attrs)?.groupValues?.get(1)?.let(::normalizeHref)
                    val filename = href?.substringAfterLast('/')?.lowercase().orEmpty()
                    val role = when {
                        "p" in classes -> TagRole.SUBJECT
                        "n" in classes -> TagRole.TEACHER
                        "s" in classes -> TagRole.ROOM
                        "o" in classes -> TagRole.CLASS
                        tagName == "a" && filename.startsWith("o") && filename.endsWith(".html") -> TagRole.CLASS
                        tagName == "a" && filename.startsWith("n") && filename.endsWith(".html") -> TagRole.TEACHER
                        tagName == "a" && filename.startsWith("s") && filename.endsWith(".html") -> TagRole.ROOM
                        else -> TagRole.NONE
                    }

                    if (role != TagRole.NONE && activeRole != role) {
                        flushRoleBuffer()
                        activeRole = role
                        activeHref = href
                    } else if (role != TagRole.NONE && activeHref == null && href != null) {
                        activeHref = href
                    }
                    tagStack.addLast(OpenTag(tagName, role, href))
                } else {
                    val idxToRemove = tagStack.indexOfLast { it.tagName == tagName }
                    if (idxToRemove >= 0) {
                        val removed = tagStack.removeAt(idxToRemove)
                        val newEffective = currentEffectiveTag()
                        val newRole = newEffective?.role ?: TagRole.NONE
                        if (removed.role != TagRole.NONE && newRole != activeRole) {
                            flushRoleBuffer()
                            activeRole = newRole
                            activeHref = newEffective?.href
                        }
                    }
                }
            }

            if (cursor < segment.length) {
                handlePlainChunk(segment.substring(cursor))
            }
            if (activeRole != TagRole.NONE) {
                flushRoleBuffer()
            }

            // Fill owner metadata when viewing teacher/room/class plan
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

            var subjectRaw = when {
                subjectParts.isNotEmpty() -> subjectParts.joinToString(" ")
                untaggedParts.isNotEmpty() -> untaggedParts.joinToString(" ")
                else -> cleanText(segment)
            }

            if (ownerEntry.category != PlanCategory.CLASS && classNames.isEmpty()) {
                val leadingMatch = leadingClassInSubjectRegex.find(subjectRaw)
                if (leadingMatch != null) {
                    classNames.add(leadingMatch.groupValues[1])
                    subjectRaw = leadingMatch.groupValues[2].trim()
                }
            }

            // If a teacher/room plan put "-1/2" on the subject instead of the class, move it to the class badge
            val subjectGroupMatch = Regex("""-(\d+/\d+)$""").find(subjectRaw)
            if (subjectGroupMatch != null) {
                val grp = subjectGroupMatch.groupValues[1]
                if (extractedGroupLabel == null) extractedGroupLabel = grp
                if (ownerEntry.category != PlanCategory.CLASS &&
                    classNames.size == 1 &&
                    !classNames[0].contains("/")
                ) {
                    classNames[0] = "${classNames[0]}-$grp"
                    subjectRaw = subjectRaw.removeSuffix("-$grp").trim()
                }
            }

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
                        groupLabel = extractedGroupLabel
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
