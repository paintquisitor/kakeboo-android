package pl.kakebo.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pl.kakebo.data.repository.EntryRepository
import pl.kakebo.domain.ExpenseSubcategory

@RunWith(AndroidJUnit4::class)
class DiaryDatabaseTest {
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KakeboDatabase::class.java)

    @Test
    fun migrationPreservesLedgerAndPlanWithUnassignedLegacyCategory() {
        val name = "diary-migration-test.db"
        migration.createDatabase(name, 2).apply {
            execSQL("INSERT INTO entries VALUES (1, 12345, 'EXPENSE', 20724, 'Zakupy')")
            execSQL("INSERT INTO monthly_plans VALUES ('2026-09', 50000, 'Cel')")
            execSQL("INSERT INTO planned_items (id, month, type, title, amountGrosz, dateEpochDay) VALUES (1, '2026-09', 'INCOME', 'Pensja', 500000, 20724)")
            close()
        }
        migration.runMigrationsAndValidate(name, 3, true, KakeboDatabase.MIGRATION_2_3).use { db ->
            db.query("SELECT amountGrosz, note, subcategory FROM entries WHERE id = 1").use {
                assertTrue(it.moveToFirst()); assertEquals(12345L, it.getLong(0))
                assertEquals("Zakupy", it.getString(1)); assertTrue(it.isNull(2))
            }
            db.query("SELECT savingsGrosz FROM monthly_plans").use {
                assertTrue(it.moveToFirst()); assertEquals(50000L, it.getLong(0))
            }
            db.query("SELECT title FROM planned_items").use {
                assertTrue(it.moveToFirst()); assertEquals("Pensja", it.getString(0))
            }
        }
    }

    @Test
    fun fullMigrationFromFirstVersionPreservesEntries() {
        val name = "diary-migration-v1-test.db"
        migration.createDatabase(name, 1).apply {
            execSQL("INSERT INTO entries VALUES (1, 100, 'EXPENSE', 20724, '')")
            close()
        }
        migration.runMigrationsAndValidate(name, 3, true, KakeboDatabase.MIGRATION_1_2, KakeboDatabase.MIGRATION_2_3).close()
    }

    @Test
    fun expensesPersistAndRespectMonthBoundariesWhenEditedAndDeleted() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "diary-persistence-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, KakeboDatabase::class.java, name).build()
        var db = open()
        val september = YearMonth.of(2026, 9)
        fun entry(date: String, sub: ExpenseSubcategory) = EntryEntity(amountGrosz = 1234,
            type = EntryType.EXPENSE, dateEpochDay = LocalDate.parse(date).toEpochDay(), subcategory = sub)
        try {
            val dao = db.entryDao()
            dao.insert(entry("2026-08-31", ExpenseSubcategory.BOOKS))
            dao.insert(entry("2026-09-01", ExpenseSubcategory.GAMES))
            dao.insert(entry("2026-09-30", ExpenseSubcategory.DINING))
            dao.insert(entry("2026-10-01", ExpenseSubcategory.DINING))
            dao.insert(EntryEntity(amountGrosz = 50000, type = EntryType.INCOME, dateEpochDay = september.atDay(10).toEpochDay()))
            db.close(); db = open()
            val repository = EntryRepository(db.entryDao())
            val entries = repository.observeMonth(september).first()
            assertEquals(listOf(ExpenseSubcategory.DINING, ExpenseSubcategory.GAMES), entries.map { it.subcategory })
            assertEquals(2468L, entries.sumOf { it.amountGrosz })
            repository.save(entries.last().copy(subcategory = ExpenseSubcategory.DINING, amountGrosz = 4500,
                dateEpochDay = september.plusMonths(1).atDay(2).toEpochDay()))
            assertEquals(1, repository.observeMonth(september).first().size)
            val october = repository.observeMonth(september.plusMonths(1)).first()
            assertEquals(2, october.size)
            assertEquals(ExpenseSubcategory.DINING, october.first().subcategory)
            assertEquals(4500L, october.first().amountGrosz)
            repository.delete(entries.first().id)
            assertTrue(repository.observeMonth(september).first().isEmpty())
            assertEquals(4, db.entryDao().observeCount().first())
        } finally {
            db.close(); context.deleteDatabase(name)
        }
    }
}
