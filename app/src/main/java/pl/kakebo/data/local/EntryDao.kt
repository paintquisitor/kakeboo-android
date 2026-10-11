package pl.kakebo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow

@Dao
abstract class EntryDao {
    @Query("SELECT * FROM entries WHERE type = 'EXPENSE' AND dateEpochDay >= :start AND dateEpochDay < :end ORDER BY dateEpochDay DESC, id DESC")
    abstract fun observeExpenses(start: Long, end: Long): Flow<List<EntryEntity>>

    @Update
    protected abstract suspend fun updateRow(entry: EntryEntity): Int

    @Query("SELECT * FROM entries WHERE id = :id")
    abstract suspend fun findById(id: Long): EntryEntity?

    @Query("SELECT COUNT(*) FROM entries WHERE receiptName = :name")
    abstract suspend fun receiptReferences(name: String): Int

    @Query("SELECT * FROM entries ORDER BY dateEpochDay DESC, id DESC")
    abstract fun observeAll(): Flow<List<EntryEntity>>

    @Query("SELECT COUNT(*) FROM entries")
    abstract fun observeCount(): Flow<Int>

    @Insert
    protected abstract suspend fun insertRow(entry: EntryEntity): Long

    @Insert
    protected abstract suspend fun insertRows(entries: List<EntryEntity>): List<Long>

    @Query("DELETE FROM entries WHERE id = :id")
    abstract suspend fun deleteById(id: Long)


    @Query("SELECT * FROM planned_items WHERE id = :id")
    protected abstract suspend fun paymentPlan(id: Long): PlannedItemEntity?

    private suspend fun validatePayment(entry: EntryEntity) {
        entry.plannedItemId?.let { id ->
            val plan = requireNotNull(paymentPlan(id)) { "Rachunek nie istnieje." }
            require(entry.type == EntryType.EXPENSE && plan.type == EntryType.EXPENSE)
            require(plan.month == YearMonth.from(LocalDate.ofEpochDay(entry.dateEpochDay)).toString()) {
                "Płatność musi należeć do miesiąca rachunku."
            }
        }
    }

    @Transaction
    open suspend fun insert(entry: EntryEntity): Long {
        validatePayment(entry)
        return insertRow(entry)
    }

    @Transaction
    open suspend fun insertAll(entries: List<EntryEntity>): List<Long> {
        entries.forEach { validatePayment(it) }
        return insertRows(entries)
    }

    @Transaction
    open suspend fun update(entry: EntryEntity) {
        validatePayment(entry)
        check(updateRow(entry) == 1) { "Wydatek nie istnieje." }
    }
}
