package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plan_index")
data class PlanIndexEntity(
    @PrimaryKey val href: String,
    val name: String,
    val category: String, // CLASS, TEACHER, ROOM
    val sortOrder: Int
)

@Entity(tableName = "cached_plans")
data class CachedPlanEntity(
    @PrimaryKey val href: String,
    val title: String,
    val category: String,
    val generatedAt: String,
    val contentHash: String,
    val payloadJson: String,
    val fetchedAtEpochMs: Long
)

@Entity(tableName = "schedule_changes")
data class ScheduleChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planHref: String,
    val planTitle: String,
    val summary: String,
    val detectedAtEpochMs: Long,
    val isRead: Boolean = false
)

@Entity(tableName = "director_free_days")
data class DirectorFreeDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val name: String
)
