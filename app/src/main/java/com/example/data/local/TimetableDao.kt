package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TimetableDao {

    @Query("SELECT * FROM plan_index ORDER BY sortOrder ASC")
    fun observeIndex(): Flow<List<PlanIndexEntity>>

    @Query("SELECT * FROM plan_index ORDER BY sortOrder ASC")
    suspend fun getIndexOnce(): List<PlanIndexEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIndexEntries(entries: List<PlanIndexEntity>)

    @Query("DELETE FROM plan_index")
    suspend fun clearIndex()

    @Transaction
    suspend fun replaceIndex(entries: List<PlanIndexEntity>) {
        clearIndex()
        insertIndexEntries(entries)
    }

    @Query("SELECT * FROM cached_plans WHERE href = :href")
    fun observePlan(href: String): Flow<CachedPlanEntity?>

    @Query("SELECT * FROM cached_plans WHERE href = :href")
    suspend fun getPlanOnce(href: String): CachedPlanEntity?

    @Query("SELECT * FROM cached_plans WHERE category = :category")
    fun observePlansByCategory(category: String): Flow<List<CachedPlanEntity>>

    @Query("SELECT * FROM cached_plans WHERE category = :category")
    suspend fun getPlansByCategoryOnce(category: String): List<CachedPlanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlan(plan: CachedPlanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlans(plans: List<CachedPlanEntity>)

    @Query("DELETE FROM cached_plans")
    suspend fun clearCachedPlans()

    @Query("SELECT * FROM schedule_changes ORDER BY detectedAtEpochMs DESC LIMIT 50")
    fun observeScheduleChanges(): Flow<List<ScheduleChangeEntity>>

    @Query("SELECT COUNT(*) FROM schedule_changes WHERE isRead = 0")
    fun observeUnreadChangesCount(): Flow<Int>

    @Insert
    suspend fun insertScheduleChanges(changes: List<ScheduleChangeEntity>)

    @Query("UPDATE schedule_changes SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllChangesRead()

    @Query("DELETE FROM schedule_changes")
    suspend fun clearScheduleChanges()

    @Query("SELECT * FROM director_free_days ORDER BY epochDay ASC")
    fun observeDirectorFreeDays(): Flow<List<DirectorFreeDayEntity>>

    @Query("SELECT * FROM director_free_days ORDER BY epochDay ASC")
    suspend fun getDirectorFreeDaysOnce(): List<DirectorFreeDayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDirectorFreeDay(day: DirectorFreeDayEntity)

    @Query("DELETE FROM director_free_days WHERE id = :id")
    suspend fun deleteDirectorFreeDay(id: Long)
}
