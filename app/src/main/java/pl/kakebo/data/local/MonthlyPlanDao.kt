package pl.kakebo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

@Dao
abstract class MonthlyPlanDao {
    @Transaction
    @Query("SELECT * FROM monthly_plans WHERE month = :month")
    abstract fun observeMonth(month: String): Flow<MonthlyPlanWithItems?>

    @Transaction
    @Query("SELECT * FROM monthly_plans WHERE month >= :startMonth AND month < :endMonth ORDER BY month")
    abstract fun observeRange(startMonth: String, endMonth: String): Flow<List<MonthlyPlanWithItems>>

    @Transaction
    @Query("SELECT * FROM monthly_plans WHERE month = :month")
    protected abstract suspend fun findMonth(month: String): MonthlyPlanWithItems?

    @Transaction
    @Query("SELECT * FROM monthly_plans WHERE month < :month ORDER BY month DESC LIMIT 1")
    protected abstract suspend fun findPreviousMonth(month: String): MonthlyPlanWithItems?

    @Query("DELETE FROM monthly_plans WHERE month > :currentMonth")
    abstract suspend fun deletePlansAfter(currentMonth: String)

    // One transaction prevents duplicate copies when Plan, Weeks and Balance load together.
    @Transaction
    open suspend fun carryForward(month: String) {
        val target = YearMonth.parse(month)
        if (findMonth(month) != null) return
        val previous = findPreviousMonth(month) ?: return
        ensurePlan(previous.plan.copy(month = month))
        previous.items.forEach { item ->
            val day = LocalDate.ofEpochDay(item.dateEpochDay).dayOfMonth.coerceAtMost(target.lengthOfMonth())
            upsertItem(item.copy(id = 0, month = month, dateEpochDay = target.atDay(day).toEpochDay()))
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun ensurePlan(plan: MonthlyPlanEntity)

    @Upsert
    abstract suspend fun savePlan(plan: MonthlyPlanEntity)

    @Upsert
    protected abstract suspend fun upsertItem(item: PlannedItemEntity)

    @Transaction
    open suspend fun saveItem(item: PlannedItemEntity) {
        if (hasPayments(item.id)) {
            val previous = requireNotNull(findItem(item.id))
            require(item.month == previous.month && item.type == EntryType.EXPENSE)
        }
        ensurePlan(MonthlyPlanEntity(item.month))
        upsertItem(item)
    }

    @Query("SELECT EXISTS(SELECT 1 FROM entries WHERE plannedItemId = :id)")
    protected abstract suspend fun hasPayments(id: Long): Boolean

    @Query("SELECT * FROM planned_items WHERE id = :id")
    protected abstract suspend fun findItem(id: Long): PlannedItemEntity?

    @Query("DELETE FROM planned_items WHERE id = :id AND month = :month")
    abstract suspend fun deleteItem(id: Long, month: String)
}
