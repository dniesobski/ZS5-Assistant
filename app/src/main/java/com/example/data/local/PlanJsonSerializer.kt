package com.example.data.local

import com.example.model.BellSlot
import com.example.model.LessonCell
import com.example.model.LessonSubgroup
import com.example.model.ParsedPlan
import com.example.model.PlanCategory
import org.json.JSONArray
import org.json.JSONObject

object PlanJsonSerializer {

    fun toJson(plan: ParsedPlan): String {
        val root = JSONObject()
        root.put("href", plan.href)
        root.put("title", plan.title)
        root.put("category", plan.category.name)
        root.put("generatedAt", plan.generatedAt)
        root.put("contentHash", plan.contentHash)
        root.put("fetchedAtEpochMs", plan.fetchedAtEpochMs)

        val bellsArr = JSONArray()
        plan.bellSlots.forEach { slot ->
            bellsArr.put(JSONObject().apply {
                put("lessonNumber", slot.lessonNumber)
                put("timeRange", slot.timeRange)
                put("startMinutes", slot.startMinutes)
                put("endMinutes", slot.endMinutes)
            })
        }
        root.put("bellSlots", bellsArr)

        val lessonsArr = JSONArray()
        plan.lessons.forEach { cell ->
            val subArr = JSONArray()
            cell.subgroups.forEach { sg ->
                subArr.put(JSONObject().apply {
                    put("subject", sg.subject)
                    put("teacherName", sg.teacherName ?: JSONObject.NULL)
                    put("teacherHref", sg.teacherHref ?: JSONObject.NULL)
                    put("roomName", sg.roomName ?: JSONObject.NULL)
                    put("roomHref", sg.roomHref ?: JSONObject.NULL)
                    put("className", sg.className ?: JSONObject.NULL)
                    put("classHref", sg.classHref ?: JSONObject.NULL)
                    put("groupLabel", sg.groupLabel ?: JSONObject.NULL)
                })
            }
            lessonsArr.put(JSONObject().apply {
                put("lessonNumber", cell.lessonNumber)
                put("timeRange", cell.timeRange)
                put("startMinutes", cell.startMinutes)
                put("endMinutes", cell.endMinutes)
                put("dayOfWeek", cell.dayOfWeek)
                put("subgroups", subArr)
            })
        }
        root.put("lessons", lessonsArr)
        return root.toString()
    }

    fun fromJson(json: String): ParsedPlan {
        val root = JSONObject(json)
        val href = root.getString("href")
        val title = root.getString("title")
        val category = PlanCategory.valueOf(root.getString("category"))
        val generatedAt = root.optString("generatedAt", "")
        val contentHash = root.optString("contentHash", "")
        val fetchedAtEpochMs = root.optLong("fetchedAtEpochMs", 0L)

        val bellsArr = root.optJSONArray("bellSlots") ?: JSONArray()
        val bellSlots = buildList {
            for (i in 0 until bellsArr.length()) {
                val obj = bellsArr.getJSONObject(i)
                add(
                    BellSlot(
                        lessonNumber = obj.getInt("lessonNumber"),
                        timeRange = obj.getString("timeRange"),
                        startMinutes = obj.getInt("startMinutes"),
                        endMinutes = obj.getInt("endMinutes")
                    )
                )
            }
        }

        val lessonsArr = root.optJSONArray("lessons") ?: JSONArray()
        val lessons = buildList {
            for (i in 0 until lessonsArr.length()) {
                val obj = lessonsArr.getJSONObject(i)
                val subArr = obj.optJSONArray("subgroups") ?: JSONArray()
                val subgroups = buildList {
                    for (j in 0 until subArr.length()) {
                        val sg = subArr.getJSONObject(j)
                        add(
                            LessonSubgroup(
                                subject = sg.getString("subject"),
                                teacherName = sg.optNullableString("teacherName"),
                                teacherHref = sg.optNullableString("teacherHref"),
                                roomName = sg.optNullableString("roomName"),
                                roomHref = sg.optNullableString("roomHref"),
                                className = sg.optNullableString("className"),
                                classHref = sg.optNullableString("classHref"),
                                groupLabel = sg.optNullableString("groupLabel")
                            )
                        )
                    }
                }
                add(
                    LessonCell(
                        lessonNumber = obj.getInt("lessonNumber"),
                        timeRange = obj.getString("timeRange"),
                        startMinutes = obj.getInt("startMinutes"),
                        endMinutes = obj.getInt("endMinutes"),
                        dayOfWeek = obj.getInt("dayOfWeek"),
                        subgroups = subgroups
                    )
                )
            }
        }

        return ParsedPlan(
            href = href,
            title = title,
            category = category,
            generatedAt = generatedAt,
            bellSlots = bellSlots,
            lessons = lessons,
            contentHash = contentHash,
            fetchedAtEpochMs = fetchedAtEpochMs
        )
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() && it != "null" }
    }
}
