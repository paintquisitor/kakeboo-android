package pl.kakebo.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pl.kakebo.domain.ExpenseSubcategory

@RunWith(AndroidJUnit4::class)
class PaymentDatabaseTest {
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KakeboDatabase::class.java)

    @Test fun migrationKeepsExistingIdsCategoriesAndPlans() {
        val name = "payment-migration-test.db"
        migration.createDatabase(name, 3).apply {
            execSQL("INSERT INTO entries VALUES (42, 12345, 'EXPENSE', 20724, 'Gra', 'GAMES')")
            execSQL("INSERT INTO monthly_plans VALUES ('2026-09', 50000, 'Cel')")
            execSQL("INSERT INTO planned_items (id, month, type, title, amountGrosz, dateEpochDay) VALUES (10, '2026-09', 'EXPENSE', 'Czynsz', 100000, 20724)")
            close()
        }
        migration.runMigrationsAndValidate(name, 4, true, KakeboDatabase.MIGRATION_3_4).use { db ->
            db.query("SELECT amountGrosz, note, subcategory, plannedItemId FROM entries WHERE id = 42").use {
                assertTrue(it.moveToFirst()); assertEquals(12345L, it.getLong(0)); assertEquals("Gra", it.getString(1))
                assertEquals("GAMES", it.getString(2)); assertTrue(it.isNull(3))
            }
            db.query("SELECT title FROM planned_items WHERE id = 10").use {
                assertTrue(it.moveToFirst()); assertEquals("Czynsz", it.getString(0))
            }
        }
    }

    @Test fun migrationFromFirstVersionReachesCurrentSchema() {
        val name = "payment-migration-v1-test.db"
        migration.createDatabase(name, 1).close()
        migration.runMigrationsAndValidate(name, 6, true, KakeboDatabase.MIGRATION_1_2,
            KakeboDatabase.MIGRATION_2_3, KakeboDatabase.MIGRATION_3_4, KakeboDatabase.MIGRATION_4_5, KakeboDatabase.MIGRATION_5_6).close()
    }

    @Test fun paymentLinksValidateMonthAndTypeAndPlanDeletionPreservesExpense() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), KakeboDatabase::class.java).build()
        val month = YearMonth.of(2026, 9)
        try {
            val bill = PlannedItemEntity(1, month.toString(), EntryType.EXPENSE, "Czynsz", 100000, month.atDay(1).toEpochDay())
            db.monthlyPlanDao().saveItem(bill)
            db.monthlyPlanDao().saveItem(bill.copy(id = 2, type = EntryType.INCOME, title = "Pensja"))
            val payment = EntryEntity(amountGrosz = 50000, type = EntryType.EXPENSE,
                dateEpochDay = month.atDay(10).toEpochDay(), subcategory = ExpenseSubcategory.FOOD, plannedItemId = 1)
            val id = db.entryDao().insert(payment)
            suspend fun rejected(block: suspend () -> Unit) {
                try { block(); fail("Invalid payment accepted") } catch (_: IllegalArgumentException) { }
            }
            rejected { db.entryDao().insert(payment.copy(plannedItemId = 999)) }
            rejected { db.entryDao().insert(payment.copy(plannedItemId = 2)) }
            rejected { db.entryDao().update(payment.copy(id = id, dateEpochDay = month.plusMonths(1).atDay(1).toEpochDay())) }
            rejected { db.monthlyPlanDao().saveItem(bill.copy(type = EntryType.INCOME)) }
            assertEquals(1, db.entryDao().observeCount().first())
            assertEquals(month.atDay(10).toEpochDay(), db.entryDao().observeAll().first().single().dateEpochDay)
            db.monthlyPlanDao().deleteItem(1, month.toString())
            val retained = db.entryDao().observeAll().first().single()
            assertNull(retained.plannedItemId)
            assertEquals(50000L, retained.amountGrosz)
            assertEquals(ExpenseSubcategory.FOOD, retained.subcategory)
        } finally { db.close() }
    }
}
