package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.network.ApkUpdateInstaller
import com.example.data.network.GitHubReleaseChecker
import com.example.data.network.OptivumHtmlParser
import com.example.data.repository.PolishCalendarCalculator
import com.example.data.repository.TimetableRepository
import com.example.model.HolidayEvent
import com.example.model.PlanCategory
import com.example.model.PlanEntry
import com.example.model.SimpleDate
import com.example.ui.screens.formatBreakCountdown
import com.example.ui.screens.formatBreakTimerDigital
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Zs5CoreLogicTest {

    @Test
    fun `github release semver comparison and json parsing work accurately`() {
        assertEquals("1.2.3", GitHubReleaseChecker.normalizeVersion("v1.2.3-beta"))
        assertTrue(GitHubReleaseChecker.compareVersions("1.0.0", "1.0.1") > 0)
        assertTrue(GitHubReleaseChecker.compareVersions("1.0.0", "v1.2.0") > 0)
        assertEquals(0, GitHubReleaseChecker.compareVersions("1.2.0", "v1.2.0"))
        assertTrue(GitHubReleaseChecker.compareVersions("1.2.0", "1.1.9") < 0)

        val json = """
            {
              "tag_name": "v1.3.0",
              "name": "Wydanie 1.3.0",
              "body": "Poprawki planu lekcji",
              "html_url": "https://github.com/dniesobski/ZS5-Assistant/releases/tag/v1.3.0",
              "assets": [
                {
                  "name": "zs5-asystent-v1.3.0.apk",
                  "browser_download_url": "https://github.com/dniesobski/ZS5-Assistant/releases/download/v1.3.0/zs5-asystent-v1.3.0.apk"
                }
              ]
            }
        """.trimIndent()
        val parsed = GitHubReleaseChecker.parseReleaseJson(json)
        assertEquals("1.3.0", parsed.normalizedVersion)
        assertEquals("https://github.com/dniesobski/ZS5-Assistant/releases/download/v1.3.0/zs5-asystent-v1.3.0.apk", parsed.apkDownloadUrl)
        assertEquals("1.0.8", GitHubReleaseChecker.normalizeVersion("v1.0.8"))
    }

    @Test
    fun `apk update installer validates pk header and creates file provider install intent`() {
        assertTrue(ApkUpdateInstaller.isValidApkHeader(byteArrayOf(0x50, 0x4B, 0x03, 0x04)))
        assertFalse(ApkUpdateInstaller.isValidApkHeader("<html>Not Found</html>".toByteArray()))
        assertFalse(ApkUpdateInstaller.isValidApkHeader(byteArrayOf(0x50)))

        val context = ApplicationProvider.getApplicationContext<Context>()
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val dummyApk = File(updatesDir, "ZS5Assist-update.apk").apply {
            writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
        }

        val intent = ApkUpdateInstaller.buildInstallApkIntent(context, dummyApk)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("application/vnd.android.package-archive", intent.type)
        assertTrue((intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
        assertNotNull(intent.data)
        assertEquals("${context.packageName}.fileprovider", intent.data?.authority)
    }

    @Test
    fun `break countdown formats minutes and seconds accurately`() {
        assertEquals("4 min 09 s", formatBreakCountdown(249))
        assertEquals("04:09", formatBreakTimerDigital(249))
        assertEquals("0 min 00 s", formatBreakCountdown(-5))
        assertEquals("00:00", formatBreakTimerDigital(-5))
    }

    @Test
    fun `optivum parser extracts classes teachers and rooms from lista html`() {
        val sampleLista = """
            <html><body>
            <ul>
              <li><a href="plany/o1.html" target="plan">1Ti</a></li>
              <li><a href="plany/n12.html" target="plan">J. Kowalski (JK)</a></li>
              <li><a href="plany/s5.html" target="plan">204</a></li>
            </ul>
            </body></html>
        """.trimIndent()

        val entries = OptivumHtmlParser.parseIndex(sampleLista)
        assertEquals(3, entries.size)
        assertEquals(PlanCategory.CLASS, entries[0].category)
        assertEquals("1Ti", entries[0].name)
        assertEquals(PlanCategory.TEACHER, entries[1].category)
        assertEquals("J. Kowalski (JK)", entries[1].name)
        assertEquals(PlanCategory.ROOM, entries[2].category)
        assertEquals("204", entries[2].name)
    }

    @Test
    fun `optivum parser extracts timetable lessons subgroups and schedule diff`() {
        val classEntry = PlanEntry("plany/o1.html", "1Ti", PlanCategory.CLASS)
        val htmlV1 = """
            <html><body>
            <span class="tytulnapis">1Ti Technik informatyk</span>
            <table class="tabela">
              <tr><th>Nr</th><th>Godz</th><th>Poniedziałek</th><th>Wtorek</th></tr>
              <tr>
                <td class="nr">1</td>
                <td class="g">8:00- 8:45</td>
                <td class="l"><span class="p">Matematyka</span> <a href="n12.html" class="n">JK</a> <a href="s5.html" class="s">204</a></td>
                <td class="l"><span class="p">Język angielski-1/2</span> <a href="n2.html" class="n">AM</a> <a href="s3.html" class="s">102</a><br><span class="p">Język niemiecki-2/2</span> <a href="n3.html" class="n">MB</a> <a href="s4.html" class="s">103</a></td>
              </tr>
            </table>
            <td class="op">Wygenerowano 2026-11-15</td>
            </body></html>
        """.trimIndent()

        val htmlV2 = htmlV1.replace(">204</a>", ">311</a>")

        val plan1 = OptivumHtmlParser.parsePlanPage(htmlV1, classEntry)
        val plan2 = OptivumHtmlParser.parsePlanPage(htmlV2, classEntry)

        assertEquals("1Ti Technik informatyk", plan1.title)
        assertEquals(2, plan1.lessons.size)
        assertEquals(2, plan1.lessons[1].subgroups.size)
        assertEquals("204", plan1.lessons[0].subgroups[0].roomName)
        assertEquals("plany/s5.html", plan1.lessons[0].subgroups[0].roomHref)

        val diffs = TimetableRepository.computeScheduleDiff(plan1, plan2)
        assertEquals(1, diffs.size)
        assertTrue(diffs[0].summary.contains("311"))
    }

    @Test
    fun `teacher locator finds active lesson and next lesson`() {
        val teacher = PlanEntry("plany/n12.html", "J. Kowalski (JK)", PlanCategory.TEACHER)
        val html = """
            <html><body>
            <span class="tytulnapis">J. Kowalski (JK)</span>
            <table class="tabela">
              <tr>
                <td class="nr">1</td>
                <td class="g">8:00- 8:45</td>
                <td class="l"><a href="o1.html" class="o">1Ti</a> <span class="p">Matematyka</span> <a href="s5.html" class="s">204</a></td>
              </tr>
              <tr>
                <td class="nr">3</td>
                <td class="g">9:50-10:35</td>
                <td class="l"><a href="o2.html" class="o">2Tp</a> <span class="p">Fizyka</span> <a href="s8.html" class="s">108</a></td>
              </tr>
            </table>
            </body></html>
        """.trimIndent()
        val parsed = OptivumHtmlParser.parsePlanPage(html, teacher)

        val duringFirst = TimetableRepository.buildTeacherLocations(
            teachers = listOf(teacher),
            teacherPlans = mapOf(teacher.href to parsed),
            dayOfWeek = 1,
            targetMinutes = 8 * 60 + 20,
            targetLessonNumber = null
        ).first()

        assertTrue(duringFirst.isCurrentlyTeaching)
        assertEquals("204", duringFirst.currentLesson?.roomName)
        assertEquals(25, duringFirst.minutesRemaining)

        val duringBreak = TimetableRepository.buildTeacherLocations(
            teachers = listOf(teacher),
            teacherPlans = mapOf(teacher.href to parsed),
            dayOfWeek = 1,
            targetMinutes = 9 * 60,
            targetLessonNumber = null
        ).first()

        assertFalse(duringBreak.isCurrentlyTeaching)
        assertNotNull(duringBreak.nextLessonToday)
        assertEquals("108", duringBreak.nextLessonToday?.second?.roomName)
    }

    @Test
    fun `polish calendar calculates easter holidays director days and summer break`() {
        // Easter 2026 is April 5
        assertEquals(SimpleDate(2026, 4, 5), PolishCalendarCalculator.easterSunday(2026))
        // End of school year 2026 is Friday June 26
        assertEquals(SimpleDate(2026, 6, 26), PolishCalendarCalculator.endOfSchoolYearFriday(2026))

        val directorDays = listOf(
            HolidayEvent(SimpleDate(2026, 11, 20), "Dzień dyrektorski", isDirectorDay = true, directorDayId = 1L)
        )
        val info = PolishCalendarCalculator.calculateCountdown(SimpleDate(2026, 11, 16), directorDays)
        assertEquals(SimpleDate(2026, 11, 20), info.nextFreeEvent?.date)
        assertEquals(4L, info.daysUntilNextFreeDay)
    }
}
